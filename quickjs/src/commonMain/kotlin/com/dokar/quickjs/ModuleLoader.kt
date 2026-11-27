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
     * Loads a normalized JavaScript module name requested by QuickJS.
     */
    @Deprecated(
        message = "Use load(name, type) instead.",
        replaceWith = ReplaceWith("load(name, ModuleImportType.Js)"),
    )
    fun load(name: String): ModuleContent? = load(name, ModuleImportType.Js)

    /**
     * Loads a normalized module name requested by QuickJS for [type].
     *
     * [name] is produced by [normalizer] when configured, or by QuickJS's
     * native default normalizer otherwise.
     *
     * Return a [ModuleContent] matching [type], or null when the module is
     * unavailable:
     *
     * - [ModuleImportType.Js]: [ModuleContent.Source] or [ModuleContent.Bytecode]
     * - [ModuleImportType.Text]: [ModuleContent.Text]
     * - [ModuleImportType.Json]: [ModuleContent.Json]
     * - [ModuleImportType.Bytes]: [ModuleContent.Bytes]
     *
     * Returning null, returning an incompatible [ModuleContent], or throwing an
     * unhandled exception fails the current compile, graph-resolution, or
     * evaluation operation and invokes [onLoadFailed] with this module name and
     * import type.
     *
     * @param name The normalized module name.
     * @param type The requested module import type.
     * @return The module content, or null when it cannot be loaded.
     */
    fun load(name: String, type: ModuleImportType): ModuleContent?

    /**
     * Reports bytecode immediately after a JavaScript source module is compiled.
     *
     * Invoked only for [ModuleImportType.Js] imports loaded from
     * [ModuleContent.Source]; data modules ([ModuleImportType.Text],
     * [ModuleImportType.Json], [ModuleImportType.Bytes]) are not compiled to
     * module bytecode and do not trigger this callback.
     *
     * QuickJs does not retain the Kotlin byte array after this call. Callers may
     * keep it or enqueue it for asynchronous persistence. Callbacks already
     * delivered for earlier modules remain valid if later graph resolution
     * fails. An unhandled exception fails the current module loading attempt and
     * invokes [onLoadFailed] with this module name and [ModuleImportType.Js].
     *
     * @param name The normalized module name.
     * @param bytecode The newly compiled module bytecode.
     */
    fun onCompiled(name: String, bytecode: ByteArray) = Unit

    /**
     * Reports that QuickJS could not load a requested module.
     */
    @Deprecated("Use onLoadFailed(name, type) instead.")
    fun onLoadFailed(name: String) = Unit

    /**
     * Reports that QuickJS could not load a requested module for [type].
     *
     * The callback is invoked when:
     *
     * - [load] returns null, returns a [ModuleContent] incompatible with [type],
     *   or throws an exception.
     * - Source, bytecode, or data returned by [load] cannot be compiled, parsed,
     *   read, or validated.
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
     * @param type The import type that failed to load.
     */
    fun onLoadFailed(name: String, type: ModuleImportType) {
        @Suppress("DEPRECATION")
        onLoadFailed(name)
    }
}

/**
 * Creates a [ModuleLoader] that only handles [ModuleImportType.Js] modules.
 */
@Deprecated("Use ModuleLoader { name, type -> ... } instead.")
fun ModuleLoader(load: (name: String) -> ModuleContent?): ModuleLoader =
    ModuleLoader { name, type ->
        if (type == ModuleImportType.Js) load(name) else null
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
    private var loadBlock: ((String, ModuleImportType) -> ModuleContent?)? = null
    private var onCompiledBlock: (String, ByteArray) -> Unit = { _, _ -> }
    private var onLoadFailedBlock: (String, ModuleImportType) -> Unit = { _, _ -> }

    /**
     * Configures custom module-name resolution.
     *
     * Without this callback, QuickJS uses its native default normalizer.
     */
    fun normalize(block: (baseName: String, requestedName: String) -> String) {
        normalizeBlock = block
    }

    /**
     * Configures synchronous JavaScript module lookup.
     */
    @Deprecated("Use load { name, type -> ... } instead.")
    fun load(block: (name: String) -> ModuleContent?) {
        loadBlock = { name, type ->
            if (type == ModuleImportType.Js) block(name) else null
        }
    }

    /**
     * Configures synchronous module lookup for all [ModuleImportType] values.
     */
    fun load(block: (name: String, type: ModuleImportType) -> ModuleContent?) {
        loadBlock = block
    }

    /**
     * Configures immediate delivery of source-compiled JavaScript module bytecode.
     */
    fun onCompiled(block: (name: String, bytecode: ByteArray) -> Unit) {
        onCompiledBlock = block
    }

    /**
     * Configures immediate notification for failed module loading attempts.
     */
    @Deprecated("Use onLoadFailed { name, type -> ... } instead.")
    fun onLoadFailed(block: (name: String) -> Unit) {
        onLoadFailedBlock = { name, _ -> block(name) }
    }

    /**
     * Configures immediate notification for failed module loading attempts.
     */
    fun onLoadFailed(block: (name: String, type: ModuleImportType) -> Unit) {
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

            override fun load(name: String, type: ModuleImportType): ModuleContent? =
                loadCallback(name, type)

            override fun onCompiled(name: String, bytecode: ByteArray) {
                onCompiledCallback(name, bytecode)
            }

            override fun onLoadFailed(name: String, type: ModuleImportType) {
                onLoadFailedCallback(name, type)
            }
        }
    }
}

/**
 * Creates a [ModuleLoader] using a small callback DSL.
 */
fun moduleLoader(block: ModuleLoaderBuilder.() -> Unit): ModuleLoader =
    ModuleLoaderBuilder().apply(block).build()
