package com.dokar.quickjs

import com.dokar.quickjs.binding.AsyncFunctionBinding
import com.dokar.quickjs.binding.Binding
import com.dokar.quickjs.binding.FunctionBinding
import com.dokar.quickjs.binding.JsObjectHandle
import com.dokar.quickjs.binding.ObjectBinding
import com.dokar.quickjs.bridge.ExecuteJobResult
import com.dokar.quickjs.bridge.JsPromise
import com.dokar.quickjs.bridge.compile
import com.dokar.quickjs.bridge.defineFunction
import com.dokar.quickjs.bridge.defineObject
import com.dokar.quickjs.bridge.evaluate
import com.dokar.quickjs.bridge.executePendingJob
import com.dokar.quickjs.bridge.invokeJsFunction
import com.dokar.quickjs.bridge.ktMemoryUsage
import com.dokar.quickjs.bridge.objectHandleToStableRef
import com.dokar.quickjs.bridge.setPromiseRejectionHandler
import com.dokar.quickjs.converter.TypeConverter
import com.dokar.quickjs.converter.TypeConverters
import com.dokar.quickjs.converter.castValueOr
import com.dokar.quickjs.converter.typeOfInstance
import com.dokar.quickjs.util.withLockSync
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import quickjs.JSContext
import quickjs.JSRuntime
import quickjs.JSValue
import quickjs.JS_FreeContext
import quickjs.JS_FreeRuntime
import quickjs.JS_FreeValue
import quickjs.JS_GetRuntime
import quickjs.JS_NewContext
import quickjs.JS_NewRuntime
import quickjs.JS_RunGC
import quickjs.JS_SetMaxStackSize
import quickjs.JS_SetMemoryLimit
import quickjs.JS_UpdateStackTop
import quickjs.quickjs_version
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.reflect.typeOf

@OptIn(ExperimentalForeignApi::class)
actual class QuickJs private constructor(
    private val jobDispatcher: CoroutineDispatcher
) {
    private val runtime: CPointer<JSRuntime> = JS_NewRuntime()
        ?: qjsError("Failed to create js runtime.")

    private val context: CPointer<JSContext> = JS_NewContext(runtime)
        ?: qjsError("Failed to create js context.")

    private val ref = StableRef.create(this)

    private var evalException: Throwable? = null
    private var currentEvaluationSession: EvaluationSession? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (evalException == null) {
            evalException = throwable
        }
    }
    private val coroutineScope = CoroutineScope(jobDispatcher + exceptionHandler)

    private val objectBindings = mutableMapOf<Long, ObjectBinding>()
    private val globalFunctions = mutableMapOf<String, Binding>()

    private val managedJsValues = mutableListOf<CValue<JSValue>>()

    private val modules = mutableListOf<ByteArray>()

    private val jobsMutex = Mutex()
    private val asyncJobs = mutableListOf<AsyncJob>()
    private val activePromises = mutableListOf<JsPromise>()

    /**
     * Avoid concurrent executions.
     */
    private val jsMutex = Mutex()
    private val rootEvaluationMutex = Mutex()
    private val runtimeProgress = MutableStateFlow(0L)

    @PublishedApi
    internal actual val typeConverters = TypeConverters()

    actual var isClosed: Boolean = false
        private set

    actual val version: String
        get() {
            ensureNotClosed()
            return quickjs_version()!!.toKStringFromUtf8()
        }

    actual var memoryLimit: Long = -1
        set(value) {
            ensureNotClosed()
            field = value
            JS_UpdateStackTop(runtime)
            JS_SetMemoryLimit(runtime, value.toULong())
        }

    actual var maxStackSize: Long = 256 * 1024L
        set(value) {
            ensureNotClosed()
            field = value
            JS_UpdateStackTop(runtime)
            JS_SetMaxStackSize(runtime, value.toULong())
        }

    actual val memoryUsage: MemoryUsage
        get() {
            ensureNotClosed()
            JS_UpdateStackTop(runtime)
            return runtime.ktMemoryUsage()
        }

    init {
        setPromiseRejectionHandler(ref, runtime)
    }

    actual fun addTypeConverters(vararg converters: TypeConverter<*, *>) {
        typeConverters.addConverters(*converters)
    }

    actual fun defineBinding(
        name: String,
        binding: ObjectBinding,
        parent: JsObjectHandle
    ): JsObjectHandle {
        return jsMutex.withLockSync {
            ensureNotClosed()
            val handle = context.defineObject(
                quickJsRef = ref,
                parentHandle = parent.nativeHandle,
                name = name,
                binding = binding,
            )
            objectBindings[handle] = binding
            JsObjectHandle(handle)
        }
    }

    actual fun <R> defineBinding(
        name: String,
        binding: FunctionBinding<R>
    ) {
        jsMutex.withLockSync {
            ensureNotClosed()
            context.defineFunction(
                quickJsRef = ref,
                parent = null,
                parentHandle = JsObjectHandle.globalThis.nativeHandle,
                name = name,
                isAsync = false,
            )
            globalFunctions[name] = binding
        }
    }

    actual fun <R> defineBinding(
        name: String,
        binding: AsyncFunctionBinding<R>
    ) {
        jsMutex.withLockSync {
            ensureNotClosed()
            context.defineFunction(
                quickJsRef = ref,
                parent = null,
                parentHandle = JsObjectHandle.globalThis.nativeHandle,
                name = name,
                isAsync = true,
            )
            globalFunctions[name] = binding
        }
    }

    @Throws(QuickJsException::class)
    actual fun addModule(name: String, code: String) {
        ensureNotClosed()
        modules.add(compile(code = code, filename = name, asModule = true))
    }

    actual fun addModule(bytecode: ByteArray) {
        ensureNotClosed()
        modules.add(bytecode)
    }

    @Throws(QuickJsException::class)
    actual fun compile(
        code: String,
        filename: String,
        asModule: Boolean
    ): ByteArray {
        ensureNotClosed()
        jsMutex.withLockSync {
            return context.compile(code = code, filename = filename, asModule = asModule)
        }
    }

    @Throws(QuickJsException::class, CancellationException::class)
    actual suspend inline fun <reified T> evaluate(bytecode: ByteArray): T {
        return castValueOr(evalInternal(bytecode = bytecode), typeOf<T>()) {
            typeConverters.convert(
                source = it,
                sourceType = typeOfInstance(typeConverters, it),
                targetType = typeOf<T>()
            )
        }
    }

    @Throws(QuickJsException::class, CancellationException::class)
    actual suspend inline fun <reified T> evaluate(
        code: String,
        filename: String,
        asModule: Boolean
    ): T {
        return castValueOr(
            evalInternal(code = code, filename = filename, asModule = asModule),
            typeOf<T>()
        ) {
            typeConverters.convert(
                source = it,
                sourceType = if (it is JsPromise) typeOf<JsPromise>() else typeOfInstance(typeConverters, it),
                targetType = typeOf<T>()
            )
        }
    }

    @PublishedApi
    @Throws(QuickJsException::class, CancellationException::class)
    internal suspend fun evalInternal(bytecode: ByteArray): Any? = evalAndAwait {
        context.evaluate(bytecode = bytecode)
    }

    @PublishedApi
    @Throws(QuickJsException::class, CancellationException::class)
    internal suspend fun evalInternal(
        code: String,
        filename: String,
        asModule: Boolean
    ): Any? = evalAndAwait {
        context.evaluate(code = code, filename = filename, asModule = asModule)
    }

    actual fun gc() {
        ensureNotClosed()
        jsMutex.withLockSync {
            if (isClosed) return@withLockSync
            JS_UpdateStackTop(runtime)
            JS_RunGC(runtime)
        }
    }

    actual fun close() {
        if (isClosed) return
        val promisesToFree = jobsMutex.withLockSync {
            if (isClosed) return@withLockSync emptyList()
            isClosed = true
            evalException = null
            asyncJobs.forEach { it.job.cancel() }
            asyncJobs.clear()
            val promises = activePromises.toList()
            activePromises.clear()
            promises
        }
        signalRuntimeProgress()
        jsMutex.withLockSync {
            promisesToFree.forEach { it.free(context) }
            managedJsValues.forEach { JS_FreeValue(context, it) }
            managedJsValues.clear()
            // Dispose stable refs
            objectBindings.keys.forEach { objectHandleToStableRef(it)?.dispose() }
            objectBindings.clear()
            globalFunctions.clear()
            JS_FreeContext(context)
            JS_FreeRuntime(runtime)
        }
        modules.clear()
        ref.dispose()
    }

    @Suppress("UNCHECKED_CAST")
    internal actual fun invokeAsyncFunction(
        args: Array<Any?>,
        block: suspend (bindingArgs: Array<Any?>) -> Any?
    ) {
        if (isClosed) return
        val session = currentEvaluationSession
            ?: qjsError("Async function was invoked outside an evaluation.")
        val resolveFunc = args[0] as CValue<JSValue>
        val rejectFunc = args[1] as CValue<JSValue>
        val job = coroutineScope.launch(context = session, start = CoroutineStart.LAZY) {
            try {
                val result = block(args.sliceArray(2..<args.size))
                jsMutex.withLock {
                    if (isClosed) return@withLock
                    withEvaluationSession(session) {
                        context.invokeJsFunction(resolveFunc, arrayOf(result))
                    }
                }
                signalRuntimeProgress()
            } catch (e: Throwable) {
                jsMutex.withLock {
                    if (isClosed) return@withLock
                    withEvaluationSession(session) {
                        context.invokeJsFunction(rejectFunc, arrayOf(e))
                    }
                }
                signalRuntimeProgress()
            }
        }
        jobsMutex.withLockSync { asyncJobs += AsyncJob(job, session) }
        job.invokeOnCompletion {
            jobsMutex.withLockSync { asyncJobs.removeAll { it.job == job } }
            signalRuntimeProgress()
        }
        job.start()
    }

    private suspend inline fun evalAndAwait(
        crossinline block: () -> JsPromise
    ): Any? {
        ensureNotClosed()
        val inheritedSession = coroutineContext[EvaluationSession]
        if (inheritedSession != null) {
            return evalInSession(inheritedSession, isRoot = false, block)
        }
        return rootEvaluationMutex.withLock {
            evalException = null
            evalInSession(EvaluationSession(), isRoot = true, block)
        }
    }

    private suspend inline fun evalInSession(
        session: EvaluationSession,
        isRoot: Boolean,
        crossinline block: () -> JsPromise,
    ): Any? {
        var resultPromise: JsPromise? = null
        var evaluation: EvaluationState? = null
        try {
            loadModules(session)
            jsMutex.withLock {
                ensureNotClosed()
                val promise = withEvaluationSession(session) { block() }
                resultPromise = promise
                val state = EvaluationState(promiseId = promise.identity)
                evaluation = state
                jobsMutex.withLock {
                    if (isClosed) {
                        promise.free(context)
                        resultPromise = null
                        throw QuickJsException("Already closed.")
                    }
                    state.exception = session.unhandledRejections.remove(state.promiseId)
                    session.evaluations[state.promiseId] = state
                    activePromises.add(promise)
                }
            }
            val promise = resultPromise ?: qjsError("Evaluation result was not initialized.")
            val state = evaluation ?: qjsError("Evaluation state was not initialized.")
            awaitEvaluateResult(session, promise, state, isRoot)
            checkException(session, state, isRoot)
            jsMutex.withLock {
                ensureNotClosed()
                JS_UpdateStackTop(JS_GetRuntime(context))
                return withEvaluationSession(session) { promise.result(context) }
            }
        } finally {
            val promise = resultPromise
            if (promise != null) {
                withContext(NonCancellable) {
                    jobsMutex.withLock {
                        evaluation?.let { session.evaluations.remove(it.promiseId) }
                        activePromises.remove(promise)
                    }
                    jsMutex.withLock {
                        if (!isClosed) promise.free(context)
                    }
                }
            }
        }
    }

    private suspend fun awaitEvaluateResult(
        session: EvaluationSession,
        resultPromise: JsPromise,
        evaluation: EvaluationState,
        isRoot: Boolean,
    ) {
        while (true) {
            val observedProgress = runtimeProgress.value
            val (resultPending, executedJobs) = jsMutex.withLock {
                if (isClosed) throw CancellationException("Already closed.")
                withEvaluationSession(session) {
                    var executedAny = false
                    do {
                        val execResult = executePendingJob(runtime)
                        if (execResult is ExecuteJobResult.Failure) {
                            throw execResult.error
                        }
                        val executed = execResult == ExecuteJobResult.Success
                        executedAny = executedAny || executed
                    } while (executed)
                    resultPromise.isPending(context) to executedAny
                }
            }

            var hasUnhandledException = false
            val activeJobs = if (isRoot) {
                jobsMutex.withLock {
                    hasUnhandledException = evaluation.exception != null ||
                            session.unhandledRejections.isNotEmpty()
                    asyncJobs.filter { it.session === session && it.job.isActive }
                        .map { it.job }
                }
            } else {
                emptyList()
            }
            if (hasUnhandledException) {
                activeJobs.forEach { it.cancel() }
                return
            }
            val hasActiveJobs = activeJobs.isNotEmpty()
            val progressUnchanged = runtimeProgress.value == observedProgress

            if (executedJobs) signalRuntimeProgress()

            if (!resultPending && !hasActiveJobs && progressUnchanged) {
                return
            }

            if (!resultPending && !hasActiveJobs) continue
            runtimeProgress.first { it != observedProgress }
        }
    }

    private suspend fun loadModules(session: EvaluationSession) = jsMutex.withLock {
        ensureNotClosed()
        withEvaluationSession(session) {
            for (module in modules) {
                context.evaluate(module).free(context)
            }
            modules.clear()
        }
    }

    private fun ensureNotClosed() {
        if (isClosed) {
            qjsError("Already closed.")
        }
    }

    private fun checkException(
        session: EvaluationSession,
        evaluation: EvaluationState,
        isRoot: Boolean,
    ) {
        val exception = jobsMutex.withLockSync {
            evaluation.exception.also { evaluation.exception = null }
                ?: if (isRoot) {
                    session.unhandledRejections.entries.firstOrNull()?.let {
                        session.unhandledRejections.remove(it.key)
                    }
                } else {
                    null
                }
                ?: evalException.also { evalException = null }
        }
        if (exception != null) throw exception
    }

    internal fun addManagedJsValues(vararg value: CValue<JSValue>) {
        managedJsValues.addAll(value)
    }

    internal fun onCallBindingGetter(
        parentHandle: Long,
        name: String,
    ): Any? {
        ensureNotClosed()
        val binding = objectBindings[parentHandle] ?: qjsError("Parent not found.")
        return binding.getter(name)
    }

    internal fun onCallBindingSetter(
        parentHandle: Long,
        name: String,
        value: Any?
    ) {
        ensureNotClosed()
        val binding = objectBindings[parentHandle] ?: qjsError("Parent not found.")
        binding.setter(name, value)
    }

    internal fun onCallBindingFunction(
        parentHandle: Long,
        name: String,
        args: Array<Any?>,
    ): Any? {
        ensureNotClosed()
        return if (parentHandle == JsObjectHandle.globalThis.nativeHandle) {
            val binding = globalFunctions[name] ?: qjsError("Global function '$name' not found.")
            when (binding) {
                is AsyncFunctionBinding<*> -> invokeAsyncFunction(args) { binding.invoke(it) }
                is FunctionBinding<*> -> binding.invoke(args)
                is ObjectBinding -> qjsError("Unexpected object binding, require a function binding.")
            }
        } else {
            val binding = objectBindings[parentHandle] ?: qjsError("Parent not found.")
            binding.invoke(name, args)
        }
    }

    internal fun setUnhandledPromiseRejection(promiseId: Long, reason: Any?) {
        if (isClosed) return
        val exception = reason as? Throwable ?: QuickJsException(reason.toString())
        val session = currentEvaluationSession
        jobsMutex.withLockSync {
            if (session == null) {
                if (evalException == null) evalException = exception
            } else {
                val evaluation = session.evaluations[promiseId]
                if (evaluation != null) {
                    if (evaluation.exception == null) evaluation.exception = exception
                } else if (promiseId !in session.unhandledRejections) {
                    session.unhandledRejections[promiseId] = exception
                }
            }
        }
        signalRuntimeProgress()
    }

    internal fun clearHandledPromiseRejection(promiseId: Long) {
        if (isClosed) return
        val session = currentEvaluationSession
        jobsMutex.withLockSync {
            if (session == null) evalException = null
            else {
                session.evaluations[promiseId]?.exception = null
                session.unhandledRejections.remove(promiseId)
            }
        }
    }

    private inline fun <T> withEvaluationSession(
        session: EvaluationSession,
        block: () -> T,
    ): T {
        val previous = currentEvaluationSession
        currentEvaluationSession = session
        return try {
            block()
        } finally {
            currentEvaluationSession = previous
        }
    }

    private fun signalRuntimeProgress() {
        runtimeProgress.update { it + 1 }
    }

    private data class AsyncJob(
        val job: Job,
        val session: EvaluationSession,
    )

    private data class EvaluationState(
        val promiseId: Long,
        var exception: Throwable? = null,
    )

    private class EvaluationSession : AbstractCoroutineContextElement(Key) {
        val evaluations = mutableMapOf<Long, EvaluationState>()
        val unhandledRejections = linkedMapOf<Long, Throwable>()

        companion object Key : CoroutineContext.Key<EvaluationSession>
    }

    actual companion object {
        @Throws(QuickJsException::class)
        actual fun create(jobDispatcher: CoroutineDispatcher): QuickJs {
            return QuickJs(jobDispatcher = jobDispatcher)
        }
    }
}
