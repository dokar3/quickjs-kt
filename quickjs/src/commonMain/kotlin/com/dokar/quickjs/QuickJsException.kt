package com.dokar.quickjs

import kotlin.jvm.JvmOverloads

/**
 * It can be thrown when initializing [QuickJs] or calling its functions.
 *
 * @param stack The JavaScript stack trace, it's also part of the [message].
 */
open class QuickJsException @JvmOverloads constructor(
    override val message: String?,
    val stack: String? = null,
) : Exception() {
    private val location = stack?.let(::firstStackLocation)

    /**
     * File the JavaScript error was thrown in, null if unknown.
     */
    val fileName: String? = location?.fileName

    /**
     * Line the JavaScript error was thrown at, null if unknown.
     */
    val lineNumber: Int? = location?.lineNumber

    /**
     * Column the JavaScript error was thrown at, null if unknown.
     */
    val columnNumber: Int? = location?.columnNumber
}

/**
 * Thrown when an evaluation was interrupted by [QuickJs.interruptEvaluation]
 * or it ran longer than [QuickJs.evaluationTimeoutMillis].
 */
class QuickJsInterruptedException(
    message: String?,
) : QuickJsException(message)

@PublishedApi
internal fun qjsError(message: String): Nothing = throw QuickJsException(message)

private class StackLocation(
    val fileName: String,
    val lineNumber: Int?,
    val columnNumber: Int?,
)

/**
 * Read the location out of the innermost stack frame, which looks like
 * '    at fn (file.js:12:5)' or '    at file.js:12:5' for parsing errors.
 */
private fun firstStackLocation(stack: String): StackLocation? {
    val frame = stack.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
    val text = frame.removePrefix("at ").let {
        if (it.endsWith(")")) it.substringAfterLast('(').dropLast(1) else it
    }
    if (text.isEmpty() || text == "native") {
        return null
    }
    // Split from the end, file names may contain colons too
    val parts = text.split(':')
    if (parts.size >= 3) {
        val line = parts[parts.size - 2].toIntOrNull()
        val column = parts[parts.size - 1].toIntOrNull()
        if (line != null && column != null) {
            return StackLocation(parts.dropLast(2).joinToString(":"), line, column)
        }
    }
    if (parts.size >= 2) {
        val line = parts.last().toIntOrNull()
        if (line != null) {
            return StackLocation(parts.dropLast(1).joinToString(":"), line, null)
        }
    }
    return StackLocation(text, null, null)
}
