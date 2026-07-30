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
 * Read the location out of the innermost stack frame that has one. Frames look
 * like '    at fn (file.js:12:5)', '    at file.js:12:5' for parsing errors, or
 * '    at fn (native)' for native functions, which carry no location at all.
 */
private fun firstStackLocation(stack: String): StackLocation? {
    // A frame naming a file but no position, '    at fn (file.js)'
    var fileOnly: StackLocation? = null
    for (line in stack.lineSequence()) {
        val frame = line.trim().removePrefix("at ")
        val parenthesized = frame.endsWith(")") && frame.contains('(')
        val text = if (parenthesized) frame.substringAfterLast('(').dropLast(1) else frame
        if (text.isEmpty() || text == "native") {
            continue
        }
        // Split from the end, file names may contain colons too
        val parts = text.split(':')
        var fileNameEnd = parts.size
        var lineNumber: Int? = null
        var columnNumber: Int? = null
        // Positions are appended as ':line' or ':line:column'
        val last = if (parts.size > 1) parts.last().toIntOrNull() else null
        if (last != null) {
            val secondLast = if (parts.size > 2) parts[parts.size - 2].toIntOrNull() else null
            if (secondLast != null) {
                lineNumber = secondLast
                columnNumber = last
                fileNameEnd -= 2
            } else {
                lineNumber = last
                fileNameEnd -= 1
            }
        }
        val fileName = parts.subList(0, fileNameEnd).joinToString(":")
        if (fileName.isEmpty()) {
            continue
        }
        if (lineNumber != null) {
            return StackLocation(fileName, lineNumber, columnNumber)
        }
        // Without parentheses this could be a function name of a frame with no
        // debug info, '    at fn', rather than a file name
        if (parenthesized && fileOnly == null) {
            fileOnly = StackLocation(fileName, null, null)
        }
    }
    return fileOnly
}
