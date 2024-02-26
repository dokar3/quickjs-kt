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
import com.dokar.quickjs.util.withLockSync
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
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
import quickjs.JS_NewContext
import quickjs.JS_NewRuntime
import quickjs.JS_RunGC
import quickjs.JS_SetMaxStackSize
import quickjs.JS_SetMemoryLimit
import quickjs.JS_UpdateStackTop
import quickjs.quickjs_version
import kotlin.coroutines.cancellation.CancellationException

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

    private val evalMutex = Mutex()

    private val closeLock = SynchronizedObject()

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
            return runtime.ktMemoryUsage()
        }

    init {
        setPromiseRejectionHandler(ref, runtime)
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
        return context.compile(code = code, filename = filename, asModule = asModule)
    }

    @Throws(QuickJsException::class, CancellationException::class)
    actual suspend inline fun <reified T> evaluate(bytecode: ByteArray): T {
        return jsAutoCastOrThrow(evalInternal(bytecode = bytecode), T::class)
    }

    @Throws(QuickJsException::class, CancellationException::class)
    actual suspend inline fun <reified T> evaluate(
        code: String,
        filename: String,
        asModule: Boolean
    ): T {
        return jsAutoCastOrThrow(
            evalInternal(code = code, filename = filename, asModule = asModule),
            T::class
        )
    }

    @PublishedApi
    @Throws(QuickJsException::class, CancellationException::class)
    internal suspend fun evalInternal(bytecode: ByteArray): Any? = evalMutex.withLock {
        return evalAndAwait {
            context.evaluate(bytecode = bytecode)
        }
    }

    @PublishedApi
    @Throws(QuickJsException::class, CancellationException::class)
    internal suspend fun evalInternal(
        code: String,
        filename: String,
        asModule: Boolean
    ): Any? = evalMutex.withLock {
        return evalAndAwait {
            context.evaluate(code = code, filename = filename, asModule = asModule)
        }
    }

    actual fun gc() {
        ensureNotClosed()
        JS_RunGC(runtime)
    }

    actual fun close() {
        if (isClosed) return
        isClosed = true
        evalException = null
        jobsMutex.withLockSync {
            asyncJobs.forEach { it.cancel() }
            asyncJobs.clear()
        }
        synchronized(closeLock) {
            modules.clear()
            managedJsValues.forEach { JS_FreeValue(context, it) }
            managedJsValues.clear()
            // Dispose stable refs
            objectBindings.keys.forEach { objectHandleToStableRef(it)?.dispose() }
            objectBindings.clear()
            globalFunctions.clear()
            JS_FreeContext(context)
            JS_FreeRuntime(runtime)
            ref.dispose()
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal actual fun invokeAsyncFunction(
        args: Array<Any?>,
        block: suspend (bindingArgs: Array<Any?>) -> Any?
    ) {
        ensureNotClosed()
        val resolveFunc = args[0] as CValue<JSValue>
        val rejectFunc = args[1] as CValue<JSValue>
        val job = coroutineScope.launch {
            try {
                val result = block(args.sliceArray(2..<args.size))
                synchronized(closeLock) {
                    context.invokeJsFunction(resolveFunc, arrayOf(result))
                }
            } catch (e: Throwable) {
                synchronized(closeLock) {
                    context.invokeJsFunction(rejectFunc, arrayOf(e))
                }
            }
            synchronized(closeLock) { executePendingJob(runtime) }
        }
        job.invokeOnCompletion {
            jobsMutex.withLockSync { asyncJobs.remove(job) }
        }
        jobsMutex.withLockSync { asyncJobs.add(job) }
    }

    private suspend inline fun evalAndAwait(block: () -> JsPromise): Any? {
        ensureNotClosed()
        evalException = null
        loadModules()
        var resultPromise: JsPromise? = null
        try {
            resultPromise = block()
            awaitAsyncJobs()
            checkException()
            return resultPromise.result(context)
        } finally {
            resultPromise?.free(context)
        }
    }

    private suspend fun awaitAsyncJobs() {
        while (true) {
            do {
                val execResult = executePendingJob(runtime)
                if (execResult is ExecuteJobResult.Failure) {
                    throw execResult.error
                }
            } while (execResult == ExecuteJobResult.Success)
            val jobs = jobsMutex.withLock { asyncJobs.filter { it.isActive } }
            if (jobs.isEmpty()) {
                // No jobs to run
                break
            }
            jobs.joinAll()
        }
    }

    private fun loadModules() {
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
        ensureNotClosed()
        if (evalException == null) {
            evalException = reason as? Throwable ?: Error(reason.toString())
        }
        jobsMutex.withLockSync { asyncJobs.forEach { it.cancel() } }
    }

    actual companion object {
        @Throws(QuickJsException::class)
        actual fun create(jobDispatcher: CoroutineDispatcher): QuickJs {
            return QuickJs(jobDispatcher = jobDispatcher)
        }
    }
}