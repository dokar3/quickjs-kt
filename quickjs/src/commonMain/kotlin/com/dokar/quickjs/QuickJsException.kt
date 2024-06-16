package com.dokar.quickjs

/**
 * It can be thrown when initializing [QuickJs] or calling its functions.
 */
class QuickJsException(
    override val message: String?,
) : Exception()

@PublishedApi
internal fun qjsError(message: String): Nothing = throw QuickJsException(message)
