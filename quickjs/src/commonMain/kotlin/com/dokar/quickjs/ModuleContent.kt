package com.dokar.quickjs

/**
 * Content returned by [ModuleLoader] when QuickJS resolves an ES module.
 */
sealed interface ModuleContent {
    /**
     * JavaScript source compiled lazily using the requested module name.
     *
     * Valid for [ModuleImportType.Js] imports.
     *
     * @param code The complete ES module source.
     */
    class Source(val code: String) : ModuleContent

    /**
     * QuickJS module bytecode reused without recompiling its source.
     *
     * Valid for [ModuleImportType.Js] imports. The bytecode must be produced by
     * a compatible QuickJS version and retain the same module name requested
     * from [ModuleLoader.load]. QuickJS does not validate bytecode for safety,
     * so it must come from a trusted, integrity-protected source.
     *
     * @param bytes The compiled ES module bytecode.
     */
    class Bytecode(val bytes: ByteArray) : ModuleContent

    /**
     * Text module content exported as a default JavaScript string.
     *
     * Valid for [ModuleImportType.Text] imports (`with { type: "text" }`).
     *
     * @param text The raw module text.
     */
    class Text(val text: String) : ModuleContent

    /**
     * JSON module content parsed strictly and exported as a default value.
     *
     * Valid for [ModuleImportType.Json] imports (`with { type: "json" }`).
     *
     * @param json The strict JSON document.
     */
    class Json(val json: String) : ModuleContent

    /**
     * Binary module content exported as a default immutable `Uint8Array`.
     *
     * Valid for [ModuleImportType.Bytes] imports (`with { type: "bytes" }`).
     *
     * @param bytes The raw module bytes.
     */
    class Bytes(val bytes: ByteArray) : ModuleContent
}
