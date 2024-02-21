package quickjs.sample.eval

class CodeSnippet(
    val title: String,
    val filename: String,
    val code: String,
    val asModule: Boolean,
    val modules: List<Module> = emptyList(),
)

sealed class Module(
    val name: String,
) {
    class ExternalModule(
        name: String,
        val fileResPath: String,
    ) : Module(name)

    class CodeModule(
        name: String,
        val code: String,
    ) : Module(name)
}

val CodeSnippets = listOf(
    CodeSnippet(
        title = "Hello world",
        filename = "main.js",
        code = """
            function hello() {
                return "Hi from JavaScript!";
            }
            hello();
         """.trimIndent(),
        asModule = false,
    ),
    CodeSnippet(
        title = "Bindings",
        filename = "bindings.js",
        code = """
            console.log("Current log level:", console.configs.level);
            console.configs.level = "Warn"
            console.log("Current log level:", console.configs.level);
            
            console.log("Nice logging");
            console.warn("Nice logging");
            console.error("Nice logging");
            
            passObject({ ok: false, error: "Don't know yet." });
            passArray([1, 2, 3, null]);
            passSet(new Set([1, 1, null]));
            passMap(new Map([["a", 1], ["b", 2], ["c", null]]));
            
            console.log("Response:", fetch("https://www.example.com"));
         """.trimIndent(),
        asModule = false,
    ),
    CodeSnippet(
        title = "ES Modules",
        filename = "modules.js",
        code = """
            import * as hello from "hello";
            
            returns(hello.greeting());
        """.trimIndent(),
        asModule = true,
        modules = listOf(
            Module.CodeModule(
                name = "hello",
                code = """
                    export function greeting() {
                        return "Hi from the hello module!";
                    }
                """.trimIndent(),
            ),
        )
    ),
    CodeSnippet(
        code = """
            console.log("Started");
            
            await Promise.all(
                [
                    delay(1000),
                    delay(2000),
                ]
            );
            
            console.log("Next");
            
            await delay(1000);
            
            console.log("Done");
        """.trimIndent(),
        title = "Async functions",
        filename = "async.js",
        asModule = false,
    ),
    CodeSnippet(
        title = "Preact-signals counter",
        filename = "counter.js",
        code = """
            import { signal, effect } from "@preact/signals-core";

            const count = signal(0);
            
            effect(() => {
              console.log("Count", count.value);
            });
            
            count.value++;
        """.trimIndent(),
        asModule = true,
        modules = listOf(
            Module.ExternalModule(
                name = "@preact/signals-core",
                fileResPath = "files/signals-core.mjs",
            ),
        )
    ),
)