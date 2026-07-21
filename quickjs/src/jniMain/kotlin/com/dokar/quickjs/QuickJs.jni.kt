package com.dokar.quickjs

import com.dokar.quickjs.binding.AsyncFunctionBinding
import com.dokar.quickjs.binding.Binding
import com.dokar.quickjs.binding.FunctionBinding
import com.dokar.quickjs.binding.JsFunction
import com.dokar.quickjs.binding.JsObjectHandle
import com.dokar.quickjs.binding.JsProperty
import com.dokar.quickjs.binding.ObjectBinding
import com.dokar.quickjs.converter.TypeConverter
import com.dokar.quickjs.converter.TypeConverters
import com.dokar.quickjs.converter.castValueOr
import com.dokar.quickjs.converter.typeOfClass
import com.dokar.quickjs.converter.typeOfInstance
import com.dokar.quickjs.util.withLockSync
import kotlinx.coroutines.CancellationException
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
import java.io.Closeable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Evaluate QuickJS-compiled bytecode.
 *
 * This function provides a [type] parameter, useful when the inline version of
 * [QuickJs.evaluate] is not available. You can use [typeOf] to get the type of a class.
 *
 * @see [QuickJs.evaluate]
 */
@Throws(QuickJsException::class)
suspend fun <T> QuickJs.evaluate(
    bytecode: ByteArray,
    type: KType
): T {
    return castValueOr(evaluateInternal(bytecode), type) {
        typeConverters.convert(
            source = it,
            sourceType = typeOfInstance(typeConverters, it),
            targetType = type
        )
    }
}

/**
 * Evaluate QuickJS-compiled bytecode.
 *
 * This function provides a [type] parameter, useful when the inline version of
 * [QuickJs.evaluate] is not available.
 *
 * @see [QuickJs.evaluate]
 */
@Throws(QuickJsException::class)
@Deprecated(
    message = "Use evaluate(ByteArray, KType) instead.",
    replaceWith = ReplaceWith("evaluate<T>(bytecode, typeOf<T>())"),
)
suspend fun <T> QuickJs.evaluate(
    bytecode: ByteArray,
    type: Class<T>
): T {
    val kType = typeOfClass(typeConverters, (type as Class<*>).kotlin)
    return castValueOr(evaluateInternal(bytecode), kType) {
        typeConverters.convert(
            source = it,
            sourceType = typeOfInstance(typeConverters, it),
            targetType = kType
        )
    }
}

/**
 * Evaluate JavaScript code.
 *
 * This function provides a [type] parameter, useful when the inline version of
 * [QuickJs.evaluate] is not available. You can use [typeOf] to get the type of a class.
 *
 * @see [QuickJs.evaluate]
 */
@Throws(QuickJsException::class)
suspend fun <T> QuickJs.evaluate(
    code: String,
    type: KType,
    filename: String = "main.js",
    asModule: Boolean = false
): T {
    return castValueOr(evaluateInternal(code, filename, asModule), type) {
        typeConverters.convert(
            source = it,
            sourceType = typeOfInstance(typeConverters, it),
            targetType = type
        )
    }
}

/**
 * Evaluate JavaScript code.
 *
 * This function provides a [type] parameter, useful when the inline version of
 * [QuickJs.evaluate] is not available.
 *
 * @see [QuickJs.evaluate]
 */
@Throws(QuickJsException::class)
@Deprecated(
    message = "Use evaluate(String, KType, String, Boolean) instead.",
    replaceWith = ReplaceWith("evaluate<T>(code, typeOf<T>())"),
)
suspend fun <T> QuickJs.evaluate(
    code: String,
    type: Class<T>,
    filename: String = "main.js",
    asModule: Boolean = false
): T {
    val kType = typeOfClass(typeConverters, (type as Class<*>).kotlin)
    return castValueOr(evaluateInternal(code, filename, asModule), kType) {
        typeConverters.convert(
            source = it,
            sourceType = typeOfInstance(typeConverters, it),
            targetType = kType,
        )
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
actual class QuickJs private constructor(
    private val jobDispatcher: CoroutineDispatcher,
) : Closeable {
    // Native pointers
    private var globals: Long = 0
    private var runtime: Long = 0
    private var context: Long = 0

    private val objectBindings = mutableMapOf<Long, ObjectBinding>()
    private val globalFunctions = mutableMapOf<String, Binding>()

    private val modules = mutableListOf<ByteArray>()

    private var evalException: Throwable? = null
    private var currentEvaluationSession: EvaluationSession? = null
    private var currentEvaluation: EvaluationState? = null

    // Coroutines and async jobs related
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (evalException == null) {
            evalException = throwable
        }
    }
    private val coroutineScope = CoroutineScope(jobDispatcher + exceptionHandler)

    /**
     * Avoid concurrent executions.
     */
    private val jsMutex = Mutex()
    private val rootEvaluationMutex = Mutex()
    private val runtimeProgress = MutableStateFlow(0L)

    private val jobsMutex = Mutex()
    private val asyncJobs = mutableListOf<AsyncJob>()
    private val activeEvaluateResults = mutableSetOf<Long>()

    @PublishedApi
    internal actual val typeConverters = TypeConverters()

    actual var isClosed: Boolean = false
        private set

    actual val version: String get() = nativeGetVersion()

    actual var memoryLimit: Long = -1L
        set(value) {
            ensureNotClosed()
            field = value
            setMemoryLimit(runtime, globals, value)
        }

    actual var maxStackSize: Long = 256 * 1024L
        set(value) {
            ensureNotClosed()
            field = value
            setMaxStackSize(runtime, globals, value)
        }

    actual val memoryUsage: MemoryUsage
        get() {
            ensureNotClosed()
            return getMemoryUsage(runtime, globals)
        }

    init {
        try {
            runtime = newRuntime()
            context = newContext(runtime)
            globals = initGlobals(
                runtime,
                arrayOf(Unit::class.java, UByteArray::class.java)
            )
        } catch (e: QuickJsException) {
            close()
            throw e
        }
    }

    actual fun addTypeConverters(vararg converters: TypeConverter<*, *>) {
        typeConverters.addConverters(*converters)
    }

    actual fun defineBinding(
        name: String,
        binding: ObjectBinding,
        parent: JsObjectHandle,
    ): JsObjectHandle {
        return jsMutex.withLockSync {
            ensureNotClosed()
            val nativeHandle = defineObject(
                globals = globals,
                context = context,
                parent = parent.nativeHandle,
                name = name,
                properties = binding.properties.toTypedArray(),
                functions = binding.functions.toTypedArray(),
            )
            if (nativeHandle < 0L) {
                throw QuickJsException("Failed to define object '$name'.")
            }
            objectBindings[nativeHandle] = binding
            JsObjectHandle(nativeHandle)
        }
    }

    actual fun <R> defineBinding(name: String, binding: FunctionBinding<R>) {
        jsMutex.withLockSync {
            ensureNotClosed()
            defineFunction(
                globals = globals,
                context = context,
                name = name,
                isAsync = false,
            )
            globalFunctions[name] = binding
        }
    }

    actual fun <R> defineBinding(name: String, binding: AsyncFunctionBinding<R>) {
        jsMutex.withLockSync {
            ensureNotClosed()
            defineFunction(
                globals = globals,
                context = context,
                name = name,
                isAsync = true,
            )
            globalFunctions[name] = binding
        }
    }

    @Throws(QuickJsException::class)
    actual fun addModule(name: String, code: String) {
        ensureNotClosed()
        val bytecode = compile(code = code, filename = name, asModule = true)
        modules.add(bytecode)
    }

    actual fun addModule(bytecode: ByteArray) {
        ensureNotClosed()
        modules.add(bytecode)
    }

    @Throws(QuickJsException::class)
    actual fun compile(code: String, filename: String, asModule: Boolean): ByteArray {
        ensureNotClosed()
        jsMutex.withLockSync {
            return compile(context, globals, filename, code, asModule)
        }
    }

    @Throws(QuickJsException::class, CancellationException::class)
    actual suspend inline fun <reified T> evaluate(bytecode: ByteArray): T {
        return castValueOr(evaluateInternal(bytecode), typeOf<T>()) {
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
        return castValueOr(evaluateInternal(code, filename, asModule), typeOf<T>()) {
            typeConverters.convert(
                source = it,
                sourceType = typeOfInstance(typeConverters, it),
                targetType = typeOf<T>()
            )
        }
    }

    @PublishedApi
    internal suspend fun evaluateInternal(bytecode: ByteArray): Any? = evalAndAwait {
        evaluateBytecode(context = context, globals = globals, buffer = bytecode)
    }

    @PublishedApi
    internal suspend fun evaluateInternal(
        code: String,
        filename: String,
        asModule: Boolean,
    ): Any? = evalAndAwait {
        evaluate(context, globals, filename, code, asModule)
    }

    private suspend fun evalAndAwait(evalBlock: suspend () -> Long): Any? {
        ensureNotClosed()
        val inheritedSession = coroutineContext[EvaluationSession]
        if (inheritedSession != null) {
            return evalInSession(inheritedSession, isRoot = false, evalBlock)
        }
        return rootEvaluationMutex.withLock {
            evalException = null
            evalInSession(EvaluationSession(), isRoot = true, evalBlock)
        }
    }

    private suspend fun evalInSession(
        session: EvaluationSession,
        isRoot: Boolean,
        evalBlock: suspend () -> Long,
    ): Any? {
        var resultHandle: Long? = null
        val evaluation = EvaluationState()
        try {
            loadModules(session)
            resultHandle = jsMutex.withLock {
                val handle = withEvaluation(session, evaluation) { evalBlock() }
                resultHandle = handle
                evaluation.handle = handle
                evaluation.promiseId = getEvaluateResultPromiseId(
                    context = context,
                    globals = globals,
                    handle = handle,
                )
                jobsMutex.withLock {
                    if (isClosed) throw CancellationException("Already closed.")
                    val rejection = session.unhandledRejections.remove(evaluation.promiseId)
                    if (evaluation.exception == null) evaluation.exception = rejection
                    session.evaluations[evaluation.promiseId] = evaluation
                    activeEvaluateResults += handle
                }
                handle
            }
            awaitEvaluateResult(session, evaluation, isRoot)
            val result = jsMutex.withLock {
                if (isClosed) throw CancellationException("Already closed.")
                withEvaluation(session, evaluation) {
                    getEvaluateResult(context, globals, resultHandle)
                }
            }
            handleException(session, evaluation, isRoot)
            return result
        } finally {
            val handle = resultHandle
            if (handle != null) {
                withContext(NonCancellable) {
                    jobsMutex.withLock {
                        session.evaluations.remove(evaluation.promiseId)
                        activeEvaluateResults.remove(handle)
                    }
                    jsMutex.withLock {
                        if (!isClosed) releaseEvaluateResult(context, globals, handle)
                    }
                }
            }
        }
    }

    actual fun gc() {
        ensureNotClosed()
        gc(runtime, globals)
    }

    actual override fun close() {
        if (isClosed) return
        isClosed = true
        signalRuntimeProgress()
        jobsMutex.withLockSync {
            asyncJobs.forEach { it.job.cancel() }
            asyncJobs.clear()
            activeEvaluateResults.clear()
        }
        jsMutex.withLockSync {
            objectBindings.clear()
            globalFunctions.clear()
            modules.clear()
            if (globals != 0L) {
                releaseGlobals(context, globals)
                globals = 0
            }
            if (context != 0L) {
                releaseContext(context)
                context = 0
            }
            if (runtime != 0L) {
                releaseRuntime(runtime)
                runtime = 0
            }
        }
    }

    private suspend fun awaitEvaluateResult(
        session: EvaluationSession,
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
                        val executed = executePendingJob(context, globals)
                        executedAny = executedAny || executed
                    } while (executed)
                    isEvaluateResultPending(context, globals, evaluation.handle) to executedAny
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

    private fun handleException(
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
        if (exception != null) {
            throw exception
        }
    }

    private suspend fun loadModules(session: EvaluationSession) = jsMutex.withLock {
        withEvaluationSession(session) {
            for (module in modules) {
                val handle = evaluateBytecode(context = context, globals = globals, buffer = module)
                releaseEvaluateResult(context, globals, handle)
            }
            modules.clear()
        }
    }

    internal actual fun invokeAsyncFunction(
        args: Array<Any?>,
        block: suspend (bindingArgs: Array<Any?>) -> Any?,
    ) {
        if (isClosed) return
        val session = currentEvaluationSession
            ?: qjsError("Async function was invoked outside an evaluation.")
        val (resolveHandle, rejectHandle) = promiseHandlesFromArgs(args)
        val job = coroutineScope.launch(context = session, start = CoroutineStart.LAZY) {
            try {
                val result = block(args.sliceArray(2..<args.size))
                jsMutex.withLock {
                    if (isClosed) return@withLock
                    withEvaluationSession(session) {
                        // Call resolve() on JNI side
                        invokeJsFunction(
                            context = context,
                            globals = globals,
                            handle = resolveHandle,
                            args = arrayOf(result)
                        )
                    }
                }
                signalRuntimeProgress()
            } catch (e: Throwable) {
                jsMutex.withLock {
                    if (isClosed) return@withLock
                    withEvaluationSession(session) {
                        // Call reject() on JNI side
                        invokeJsFunction(
                            context = context,
                            globals = globals,
                            handle = rejectHandle,
                            args = arrayOf(e)
                        )
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

    private fun promiseHandlesFromArgs(args: Array<Any?>): Pair<Long, Long> {
        require(args.size >= 2) {
            "Invoking async functions requires resolve and reject handles."
        }
        val resolveFunctionHandle = args[0]
        require(resolveFunctionHandle is Long) {
            val type = resolveFunctionHandle?.let { it::class.qualifiedName }
            "Unexpected resolve handle type $type, expected: Long"
        }
        val rejectFunctionHandle = args[1]
        require(rejectFunctionHandle is Long) {
            val type = rejectFunctionHandle?.let { it::class.qualifiedName }
            "Unexpected reject handle type $type, expected: Long"
        }
        return resolveFunctionHandle to rejectFunctionHandle
    }

    /**
     * Called from JNI.
     */
    private fun onCallGetter(
        handle: Long,
        name: String,
    ): Any? {
        ensureNotClosed()
        val binding = objectBindings[handle] ?: throw QuickJsException(
            "JavaScript called getter of '$name' on an unknown binding"
        )
        return binding.getter(name)
    }

    /**
     * Called from JNI.
     */
    private fun onCallSetter(
        handle: Long,
        name: String,
        value: Any?,
    ) {
        ensureNotClosed()
        val binding = objectBindings[handle] ?: throw QuickJsException(
            "JavaScript called setter of '$name' on an unknown binding"
        )
        binding.setter(name, value)
    }

    /**
     * Called from JNI.
     */
    private fun onCallFunction(
        handle: Long,
        name: String,
        args: Array<Any?>,
    ): Any? {
        ensureNotClosed()
        if (handle == JsObjectHandle.globalThis.nativeHandle) {
            val binding = globalFunctions[name] ?: throw QuickJsException(
                "'$name()' does not found in global functions."
            )
            return when (binding) {
                is AsyncFunctionBinding<*> -> invokeAsyncFunction(args) { binding.invoke(it) }
                is FunctionBinding<*> -> binding.invoke(args)
                is ObjectBinding -> qjsError("Object call not be invoked.")
            }
        } else {
            val binding = objectBindings[handle] ?: throw QuickJsException(
                "JavaScript called function '$name' on an unknown binding"
            )
            return binding.invoke(name, args)
        }
    }

    /**
     * Called from JNI.
     */
    private fun setEvalException(exception: Throwable) {
        ensureNotClosed()
        jobsMutex.withLockSync {
            val evaluation = currentEvaluation
            if (evaluation != null) {
                if (evaluation.exception == null) evaluation.exception = exception
            } else if (evalException == null) {
                evalException = exception
            }
        }
    }

    /**
     * Called from JNI.
     */
    private fun setUnhandledPromiseRejection(promiseId: Long, reason: Any?) {
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
                } else {
                    session.unhandledRejections.putIfAbsent(promiseId, exception)
                }
            }
        }
        signalRuntimeProgress()
    }

    /**
     * Called from JNI when a previously unhandled promise rejection is handled.
     */
    private fun clearHandledPromiseRejection(promiseId: Long) {
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

    private inline fun <T> withEvaluation(
        session: EvaluationSession,
        evaluation: EvaluationState,
        block: () -> T,
    ): T {
        val previousEvaluation = currentEvaluation
        currentEvaluation = evaluation
        return try {
            withEvaluationSession(session, block)
        } finally {
            currentEvaluation = previousEvaluation
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

    private fun ensureNotClosed() {
        if (isClosed || runtime == 0L || context == 0L || globals == 0L) {
            qjsError("Already closed.")
        }
    }

    private external fun newRuntime(): Long

    @Throws(QuickJsException::class)
    private external fun newContext(runtime: Long): Long

    private external fun initGlobals(runtime: Long, classes: Array<Class<*>>): Long

    @Throws(QuickJsException::class)
    private external fun releaseGlobals(context: Long, globals: Long)

    @Throws(QuickJsException::class)
    private external fun releaseRuntime(runtime: Long)

    @Throws(QuickJsException::class)
    private external fun releaseContext(context: Long)

    @Throws(QuickJsException::class)
    private external fun defineObject(
        globals: Long,
        context: Long,
        parent: Long,
        name: String,
        properties: Array<JsProperty>,
        functions: Array<JsFunction>,
    ): Long

    @Throws(QuickJsException::class)
    private external fun defineFunction(
        globals: Long,
        context: Long,
        name: String,
        isAsync: Boolean,
    )

    @Throws(QuickJsException::class)
    private external fun gc(runtime: Long, globals: Long)

    @Throws(QuickJsException::class)
    private external fun nativeGetVersion(): String

    @Throws(QuickJsException::class)
    private external fun setMemoryLimit(runtime: Long, globals: Long, byteCount: Long)

    @Throws(QuickJsException::class)
    private external fun setMaxStackSize(runtime: Long, globals: Long, byteCount: Long)

    @Throws(QuickJsException::class)
    private external fun getMemoryUsage(runtime: Long, globals: Long): MemoryUsage

    @Throws(QuickJsException::class)
    private external fun compile(
        context: Long,
        globals: Long,
        filename: String,
        code: String,
        asModule: Boolean
    ): ByteArray

    @Throws(QuickJsException::class)
    private external fun evaluate(
        context: Long,
        globals: Long,
        filename: String,
        code: String,
        asModule: Boolean
    ): Long

    @Throws(QuickJsException::class)
    private external fun evaluateBytecode(
        context: Long,
        globals: Long,
        buffer: ByteArray,
    ): Long

    @Throws(QuickJsException::class)
    private external fun invokeJsFunction(
        context: Long,
        globals: Long,
        handle: Long,
        args: Array<Any?>?,
    )

    @Throws(QuickJsException::class)
    private external fun executePendingJob(context: Long, globals: Long): Boolean

    @Throws(QuickJsException::class)
    private external fun getEvaluateResult(context: Long, globals: Long, handle: Long): Any?

    @Throws(QuickJsException::class)
    private external fun isEvaluateResultPending(
        context: Long,
        globals: Long,
        handle: Long,
    ): Boolean

    @Throws(QuickJsException::class)
    private external fun getEvaluateResultPromiseId(
        context: Long,
        globals: Long,
        handle: Long,
    ): Long

    private external fun releaseEvaluateResult(context: Long, globals: Long, handle: Long)

    private data class AsyncJob(
        val job: Job,
        val session: EvaluationSession,
    )

    private data class EvaluationState(
        var handle: Long = -1,
        var promiseId: Long = 0,
        var exception: Throwable? = null,
    )

    private class EvaluationSession : AbstractCoroutineContextElement(Key) {
        val evaluations = mutableMapOf<Long, EvaluationState>()
        val unhandledRejections = linkedMapOf<Long, Throwable>()

        companion object Key : CoroutineContext.Key<EvaluationSession>
    }

    actual companion object {
        init {
            NativeLibraryLoader.loadLibrary("quickjs")
        }

        @Throws(QuickJsException::class)
        actual fun create(
            jobDispatcher: CoroutineDispatcher,
        ): QuickJs = QuickJs(
            jobDispatcher = jobDispatcher,
        )
    }
}
