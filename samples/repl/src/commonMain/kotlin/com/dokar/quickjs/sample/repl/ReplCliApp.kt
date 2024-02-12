package com.dokar.quickjs.sample.repl

import com.dokar.quickjs.binding.define
import com.dokar.quickjs.quickJs
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking

class ReplCliApp : CliktCommand() {
    private val exitCommands = setOf("exit()", ".exit")

    override fun run() = runBlocking {
        quickJs {
            define("console") {
                function("log") { terminal.println(it.joinToString(" ")) }
                function("info") { terminal.println(it.joinToString(" ")) }
                function("warn") { terminal.println(it.joinToString(" ")) }
                function("error") { terminal.danger(it.joinToString(" "), stderr = true) }
            }

            while (isActive) {
                val input = terminal.prompt("qjs>") ?: break
                if (input.isBlank()) {
                    continue
                }
                if (input in exitCommands) {
                    break
                }
                val result = runCatching { evaluate<Any?>(input) }
                if (result.isSuccess) {
                    terminal.println(result.getOrNull()?.toString() ?: "")
                } else {
                    terminal.danger(result.exceptionOrNull()!!.message, stderr = true)
                }
            }
        }
    }
}
