package com.dokar.quickjs

import java.lang.Exception

/**
 * It will be thrown when initializing [QuickJs] or calling its functions.
 */
class QuickJsException(
    override val message: String?,
) : Exception()

internal fun qjsError(message: String): Nothing = throw QuickJsException(message)
