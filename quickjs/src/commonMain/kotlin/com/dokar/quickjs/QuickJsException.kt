package com.dokar.quickjs

/**
 * It can be thrown when initializing [QuickJs] or calling its functions.
 */
open class QuickJsException(
    override val message: String?,
) : Exception()

/**
 * Thrown when an evaluation was interrupted by [QuickJs.interruptEvaluation]
 * or it ran longer than [QuickJs.evaluationTimeoutMillis].
 */
class QuickJsInterruptedException(
    message: String?,
) : QuickJsException(message)

@PublishedApi
internal fun qjsError(message: String): Nothing = throw QuickJsException(message)
