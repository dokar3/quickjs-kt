package com.dokar.quickjs.binding

/**
 * A utility function binding to pass a result in a module.
 *
 * ### Example
 *
 * ```kotlin
 * quickJs {
 *     func("returns", ModuleReturns())
 *
 *     val code = """
 *         import * as hello from "hello";
 *
 *         returns(hello.greeting());
 *     """.trimIndent()
 *     val result = evaluate<String>(code = code, asModule = true)
 * }
 * ```
 */
class ModuleReturns : FunctionBinding<Any?> {
    internal var returnValue: Any? = Unset

    override fun invoke(args: Array<Any?>): Any {
        returnValue = args.firstOrNull()
        return Unit
    }

    companion object {
        internal val Unset = Any()
    }
}