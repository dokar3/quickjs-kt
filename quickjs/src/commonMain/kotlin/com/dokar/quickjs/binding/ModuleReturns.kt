package com.dokar.quickjs.binding

/**
 * Global function to pass a result in a module.
 */
internal class ModuleReturns : FunctionBinding<Any?> {
    var returnValue: Any? = null

    override fun invoke(args: Array<Any?>): Any {
        returnValue = args.firstOrNull()
        return Unit
    }
}