package com.dokar.quickjs

/**
 * Supplies ES modules to one [QuickJs] runtime.
 *
 * All callbacks run synchronously while QuickJS is resolving a module. They
 * must return quickly and must not re-enter the same [QuickJs] instance.
 */
fun interface ModuleLoader {
    /**
     * Optional custom module-name resolver.
     *
     * When null, QuickJS's native default normalizer is used unchanged.
     */
    val normalizer: ModuleNormalizer?
        get() = null

    /**
     * Loads a normalized module name requested by QuickJS.
     *
     * [name] is produced by [normalizer] when configured, or by QuickJS's
     * native default normalizer otherwise.
     *
     * Return source for a cache miss, compatible bytecode for a cache hit, or
     * null when the module is unavailable. Returning null or throwing an
     * unhandled exception fails the current compile, graph-resolution, or
     * evaluation operation and invokes [onLoadFailed] with this module name.
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
     * fails. An unhandled exception fails the current module loading attempt and
     * invokes [onLoadFailed] with this module name.
     *
     * @param name The normalized module name.
     * @param bytecode The newly compiled module bytecode.
     */
    fun onCompiled(name: String, bytecode: ByteArray) = Unit

    /**
     * Reports that QuickJS could not load a requested module.
     *
     * The callback is invoked when:
     *
     * - [load] returns null or throws an exception.
     * - Source or bytecode returned by [load] cannot be compiled, read, or validated.
     * - [onCompiled] throws an exception.
     *
     * A failure notification does not consume the original module error:
     *
     * - Static imports and unhandled dynamic imports still fail the current operation.
     * - A dynamic import handled with `try/catch` or `Promise.catch` can continue after
     *   this callback.
     *
     * The callback runs synchronously during module resolution, so callers should only
     * enqueue invalidation work and must not re-enter the same [QuickJs] instance.
     * An unhandled callback exception becomes the current module loading failure.
     *
     * @param name The normalized name of the module that failed to load.
     */
    fun onLoadFailed(name: String) = Unit
}

/**
 * Resolves an import specifier to the canonical module name used by QuickJS.
 *
 * The callback runs synchronously while QuickJS is resolving a module. It must
 * return quickly and must not re-enter the same [QuickJs] instance. The returned
 * name identifies the module for caching, cycle detection, loading, and
 * [ModuleLoader.onCompiled].
 */
fun interface ModuleNormalizer {
    /**
     * Resolves [requestedName] relative to [baseName].
     *
     * @param baseName The canonical name of the importing module.
     * @param requestedName The specifier written in the import statement.
     * @return The canonical module name passed to [ModuleLoader.load].
     */
    fun normalize(baseName: String, requestedName: String): String
}

/**
 * Configures a [ModuleLoader] with separate load and compilation callbacks.
 */
class ModuleLoaderBuilder internal constructor() {
    private var normalizeBlock: ((String, String) -> String)? = null
    private var loadBlock: ((String) -> ModuleContent?)? = null
    private var onCompiledBlock: (String, ByteArray) -> Unit = { _, _ -> }
    private var onLoadFailedBlock: (String) -> Unit = {}

    /**
     * Configures custom module-name resolution.
     *
     * Without this callback, QuickJS uses its native default normalizer.
     */
    fun normalize(block: (baseName: String, requestedName: String) -> String) {
        normalizeBlock = block
    }

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
        val normalizerCallback = normalizeBlock?.let { callback ->
            ModuleNormalizer { baseName, requestedName -> callback(baseName, requestedName) }
        }
        val onCompiledCallback = onCompiledBlock
        val onLoadFailedCallback = onLoadFailedBlock
        return object : ModuleLoader {
            override val normalizer: ModuleNormalizer? = normalizerCallback

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
