package com.dokar.quickjs

/**
 * The module type requested by an ES import statement or dynamic `import()`.
 */
enum class ModuleImportType {
    /**
     * Standard ES JavaScript module (no `type` import attribute).
     */
    Js,

    /**
     * Text module (`with { type: "text" }`), exporting a default JavaScript `String`.
     */
    Text,

    /**
     * JSON module (`with { type: "json" }`), exporting a default parsed JSON value.
     */
    Json,

    /**
     * Bytes module (`with { type: "bytes" }`), exporting a default immutable `Uint8Array`.
     */
    Bytes,
}
