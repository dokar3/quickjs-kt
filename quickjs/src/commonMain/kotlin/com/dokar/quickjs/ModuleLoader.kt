package com.dokar.quickjs

/**
 * Supplies ES modules to one [QuickJs] runtime.
 *
 * All callbacks run synchronously while QuickJS is resolving a module. They
 * must return quickly and must not re-enter the same [QuickJs] instance.
 */
fun interface ModuleLoader {
    /**
     * Loads a normalized module name requested by QuickJS.
     *
     * Return source for a cache miss, compatible bytecode for a cache hit, or
     * null when the module is unavailable. Unhandled exceptions fail the
     * current compile, graph-resolution, or evaluation operation.
     *
     * @param name The normalized module name.
     * @return The module content, or null when it cannot be loaded.
     */
    fun load(name: String): ModuleContent?

    /**
     * Reports bytecode immediately after a source module is compiled.
     *
     * QuickJs does not retain the Kotlin byte array after this call. Callers may
     * keep it or enqueue it for asynchronous persistence. Callbacks already
     * delivered for earlier modules remain valid if later graph resolution
     * fails. Unhandled exceptions fail the current operation.
     *
     * @param name The normalized module name.
     * @param bytecode The newly compiled module bytecode.
     */
    fun onCompiled(name: String, bytecode: ByteArray) = Unit

    /**
     * Reports that QuickJS could not load a requested module.
     *
     * This callback is delivered for both static and dynamic imports. A dynamic
     * import still triggers the callback when JavaScript handles its rejection
     * with `try/catch` or `Promise.catch`. The callback runs synchronously during
     * module resolution, so callers should only enqueue invalidation work and
     * must not re-enter the same [QuickJs] instance. Unhandled callback exceptions
     * become the current module loading failure.
     *
     * @param name The normalized name of the module that failed to load.
     */
    fun onLoadFailed(name: String) = Unit
}

/**
 * Configures a [ModuleLoader] with separate load and compilation callbacks.
 */
class ModuleLoaderBuilder internal constructor() {
    private var loadBlock: ((String) -> ModuleContent?)? = null
    private var onCompiledBlock: (String, ByteArray) -> Unit = { _, _ -> }
    private var onLoadFailedBlock: (String) -> Unit = {}

    /**
     * Configures synchronous module lookup.
     */
    fun load(block: (name: String) -> ModuleContent?) {
        loadBlock = block
    }

    /**
     * Configures immediate delivery of source-compiled module bytecode.
     */
    fun onCompiled(block: (name: String, bytecode: ByteArray) -> Unit) {
        onCompiledBlock = block
    }

    /**
     * Configures immediate notification for failed module loading attempts.
     */
    fun onLoadFailed(block: (name: String) -> Unit) {
        onLoadFailedBlock = block
    }

    internal fun build(): ModuleLoader {
        val loadCallback = requireNotNull(loadBlock) {
            "ModuleLoader requires a load callback."
        }
        val onCompiledCallback = onCompiledBlock
        val onLoadFailedCallback = onLoadFailedBlock
        return object : ModuleLoader {
            override fun load(name: String): ModuleContent? = loadCallback(name)

            override fun onCompiled(name: String, bytecode: ByteArray) {
                onCompiledCallback(name, bytecode)
            }

            override fun onLoadFailed(name: String) {
                onLoadFailedCallback(name)
            }
        }
    }
}

/**
 * Creates a [ModuleLoader] using a small callback DSL.
 */
fun moduleLoader(block: ModuleLoaderBuilder.() -> Unit): ModuleLoader =
    ModuleLoaderBuilder().apply(block).build()
