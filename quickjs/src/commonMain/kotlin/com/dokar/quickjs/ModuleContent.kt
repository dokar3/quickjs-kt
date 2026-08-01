package com.dokar.quickjs

/**
 * Content returned by [ModuleLoader] when QuickJS resolves an ES module.
 */
sealed interface ModuleContent {
    /**
     * JavaScript source compiled lazily using the requested module name.
     *
     * @param code The complete ES module source.
     */
    class Source(val code: String) : ModuleContent

    /**
     * QuickJS module bytecode reused without recompiling its source.
     *
     * The bytecode must be produced by a compatible QuickJS version and retain
     * the same module name requested from [ModuleLoader.load]. QuickJS does not
     * validate bytecode for safety, so it must come from a trusted,
     * integrity-protected source.
     *
     * @param bytes The compiled ES module bytecode.
     */
    class Bytecode(val bytes: ByteArray) : ModuleContent
}
