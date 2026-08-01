package com.dokar.quickjs

/**
 * Supplies ES modules to one [QuickJs] runtime.
 *
 * Both callbacks run synchronously while QuickJS is resolving a module. They
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
}

/**
 * Configures a [ModuleLoader] with separate load and compilation callbacks.
 */
class ModuleLoaderBuilder internal constructor() {
    private var loadBlock: ((String) -> ModuleContent?)? = null
    private var onCompiledBlock: (String, ByteArray) -> Unit = { _, _ -> }

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

    internal fun build(): ModuleLoader {
        val loadCallback = requireNotNull(loadBlock) {
            "ModuleLoader requires a load callback."
        }
        val onCompiledCallback = onCompiledBlock
        return object : ModuleLoader {
            override fun load(name: String): ModuleContent? = loadCallback(name)

            override fun onCompiled(name: String, bytecode: ByteArray) {
                onCompiledCallback(name, bytecode)
            }
        }
    }
}

/**
 * Creates a [ModuleLoader] using a small callback DSL.
 */
fun moduleLoader(block: ModuleLoaderBuilder.() -> Unit): ModuleLoader =
    ModuleLoaderBuilder().apply(block).build()
