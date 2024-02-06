package quickjs.sample.eval

class CodeSnippet(
    val title: String,
    val filename: String,
    val code: String,
    val asModule: Boolean,
)

val CodeSnippets = listOf(
    CodeSnippet(
        title = "Hello world",
        filename = "main.js",
        code = """
            function hello() {
                return "Hi from JavaScript!";
            }
            hello();
            await asyncError()
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
)