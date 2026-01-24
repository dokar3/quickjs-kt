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
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlin.coroutines.cancellation.CancellationException
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
    private val asyncJobs = mutableListOf<Job>()
    private val activePromises = mutableListOf<JsPromise>()

    /**
     * Avoid concurrent executions.
     */
    private val jsMutex = Mutex()

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
        ensureNotClosed()
        val handle = context.defineObject(
            quickJsRef = ref,
            parentHandle = parent.nativeHandle,
            name = name,
            binding = binding,
        )
        objectBindings[handle] = binding
        return JsObjectHandle(handle)
    }

    actual fun <R> defineBinding(
        name: String,
        binding: FunctionBinding<R>
    ) {
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

    actual fun <R> defineBinding(
        name: String,
        binding: AsyncFunctionBinding<R>
    ) {
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
            asyncJobs.forEach { it.cancel() }
            asyncJobs.clear()
            val promises = activePromises.toList()
            activePromises.clear()
            promises
        }
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
        val resolveFunc = args[0] as CValue<JSValue>
        val rejectFunc = args[1] as CValue<JSValue>
        val job = coroutineScope.launch {
            try {
                val result = block(args.sliceArray(2..<args.size))
                jsMutex.withLock {
                    if (isClosed) return@withLock
                    context.invokeJsFunction(resolveFunc, arrayOf(result))
                }
            } catch (e: Throwable) {
                jsMutex.withLock {
                    if (isClosed) return@withLock
                    context.invokeJsFunction(rejectFunc, arrayOf(e))
                }
            }
            jsMutex.withLock {
                if (isClosed) return@withLock
                // The job is completed, see what we can do next:
                // - Execute subsequent Promises
                // - Cancel all jobs and fail, if rejected and JS didn't handle it
                do {
                    val result = executePendingJob(runtime)
                } while (result == ExecuteJobResult.Success)
            }
        }
        jobsMutex.withLockSync { asyncJobs += job }
        job.invokeOnCompletion {
            jobsMutex.withLockSync { asyncJobs -= job }
        }
    }

    private suspend inline fun evalAndAwait(
        crossinline block: () -> JsPromise
    ): Any? {
        ensureNotClosed()
        evalException = null
        loadModules()
        var resultPromise: JsPromise? = null
        try {
            resultPromise = jsMutex.withLock {
                ensureNotClosed()
                val promise = block()
                jobsMutex.withLock {
                    if (isClosed) {
                        promise.free(context)
                        throw QuickJsException("Already closed.")
                    }
                    activePromises.add(promise)
                }
                promise
            }
            awaitAsyncJobs()
            checkException()
            jsMutex.withLock {
                ensureNotClosed()
                JS_UpdateStackTop(JS_GetRuntime(context))
                return resultPromise.result(context)
            }
        } finally {
            if (resultPromise != null) {
                val removed = jobsMutex.withLock { activePromises.remove(resultPromise) }
                if (removed) {
                    jsMutex.withLock {
                        resultPromise.free(context)
                    }
                }
            }
        }
    }

    private suspend fun awaitAsyncJobs() {
        jsMutex.withLock {
            if (isClosed) return@withLock
            do {
                // Execute JS Promises, putting this in while(true) is unnecessary
                // since we have the same loop after every asyncFunction call
                val execResult = executePendingJob(runtime)
                if (execResult is ExecuteJobResult.Failure) {
                    throw execResult.error
                }
            } while (execResult == ExecuteJobResult.Success)
        }
        while (true) {
            val jobs = jobsMutex.withLock { asyncJobs.filter { it.isActive } }
            if (jobs.isEmpty()) {
                // No jobs to run
                break
            }
            jobs.joinAll()
        }
    }

    private suspend fun loadModules() = jsMutex.withLock {
        ensureNotClosed()
        for (module in modules) {
            context.evaluate(module).free(context)
        }
        modules.clear()
    }

    private fun ensureNotClosed() {
        if (isClosed) {
            qjsError("Already closed.")
        }
    }

    private fun checkException() {
        val exception = evalException ?: return
        throw exception
    }

    internal fun addManagedJsValues(vararg value: CValue<JSValue>) {
        managedJsValues.addAll(value)
    }

    internal fun onCallBindingGetter(
        parentHandle: Long,
        name: String,
    ): Any? {
        val binding = objectBindings[parentHandle] ?: qjsError("Parent not found.")
        return binding.getter(name)
    }

    internal fun onCallBindingSetter(
        parentHandle: Long,
        name: String,
        value: Any?
    ) {
        val binding = objectBindings[parentHandle] ?: qjsError("Parent not found.")
        binding.setter(name, value)
    }

    internal fun onCallBindingFunction(
        parentHandle: Long,
        name: String,
        args: Array<Any?>,
    ): Any? {
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

    internal fun setUnhandledPromiseRejection(reason: Any?) {
        if (isClosed) return
        if (evalException == null) {
            evalException = reason as? Throwable ?: QuickJsException(reason.toString())
        }
        jobsMutex.withLockSync { asyncJobs.forEach { it.cancel() } }
    }

    internal fun clearHandledPromiseRejection() {
        if (isClosed) return
        evalException = null
    }

    actual companion object {
        @Throws(QuickJsException::class)
        actual fun create(jobDispatcher: CoroutineDispatcher): QuickJs {
            return QuickJs(jobDispatcher = jobDispatcher)
        }
    }
}