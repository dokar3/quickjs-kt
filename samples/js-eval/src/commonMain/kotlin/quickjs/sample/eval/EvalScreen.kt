package quickjs.sample.eval

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dokar.quickjs.binding.asyncFunc
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.func
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EvalScreen(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val ratio = width.toFloat() / height

        val containerModifier = modifier.fillMaxSize().padding(16.dp)
        if (ratio > 1f) {
            Row(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScreenContent(
                    codeModifier = Modifier.fillMaxHeight().weight(1.5f),
                    resultModifier = Modifier.fillMaxHeight().weight(1f),
                )
            }
        } else {
            Column(
                modifier = containerModifier,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScreenContent(
                    codeModifier = Modifier.fillMaxWidth().weight(1.5f),
                    resultModifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    codeModifier: Modifier = Modifier,
    resultModifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    var isShowSnippetSelector by remember { mutableStateOf(false) }

    var codeSnippet by remember { mutableStateOf(CodeSnippets.first()) }

    var code by remember(codeSnippet) { mutableStateOf(codeSnippet.code) }

    var logLevel by remember { mutableStateOf(LogLevel.Info) }

    val logs = remember { mutableStateListOf<LogItem>() }

    var result by remember { mutableStateOf(Result.success("")) }

    var executionTime by remember { mutableLongStateOf(-1L) }

    var isExecuting by remember { mutableStateOf(false) }

    var isRepeating by remember { mutableStateOf(false) }

    suspend fun eval() {
        logs.clear()
        result = Result.success("")
        isExecuting = true
        val start = System.currentTimeMillis()
        result = runCatching {
            quickJs {
                addModule(
                    name = "hello",
                    code = """
                        export function greeting() {
                            return "Hi from the hello module!";
                        }
                    """.trimIndent(),
                )

                define("console") {
                    // Define a nested object
                    define("configs") {
                        prop("level") {
                            getter { logLevel.name }
                            setter { logLevel = LogLevel.valueOf(it) }
                        }
                    }

                    func<Unit>("log") {
                        logs.add(LogItem.info(it.joinToString(" ")))
                    }

                    func<Unit>("warn") {
                        logs.add(LogItem.warn(it.joinToString(" ")))
                    }

                    func<Unit>("error") {
                        logs.add(LogItem.error(it.joinToString(" ")))
                    }
                }

                asyncFunc("delay") {
                    val millis = it.firstOrNull()
                    require(millis is Long && it.size == 1) {
                        "Delay requires exactly 1 int parameter."
                    }
                    require(millis >= 0) {
                        "Delay millis can't be negative."
                    }
                    delay(millis)
                }

                asyncFunc("asyncError") { error(it.joinToString(" ")) }

                func("passObject") {
                    require(it.isNotEmpty())
                    println(it[0])
                }

                @Suppress("unchecked_cast")
                func("passArray") {
                    require(it.isNotEmpty())
                    println((it[0] as Array<Any?>).contentToString())
                }

                func("passSet") {
                    require(it.isNotEmpty())
                    println(it[0])
                }

                func("passMap") {
                    require(it.isNotEmpty())
                    println(it[0])
                }

                func("fetch") { "Hey, your IP is: 192.168.2.100" }

                evaluate<Any?>(
                    code = code,
                    filename = codeSnippet.filename,
                    asModule = codeSnippet.asModule,
                )
            }.toString()
        }
        val end = System.currentTimeMillis()
        executionTime = end - start
        isExecuting = false
    }

    LaunchedEffect(isRepeating) {
        if (!isRepeating) return@LaunchedEffect
        while (true) {
            eval()
            delay(100)
        }
    }

    Column(modifier = codeModifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    )
                    .clickable { isShowSnippetSelector = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = codeSnippet.title)

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Code snippets",
                )

                DropdownMenu(
                    expanded = isShowSnippetSelector,
                    onDismissRequest = { isShowSnippetSelector = false },
                ) {
                    for (snippet in CodeSnippets) {
                        DropdownMenuItem(
                            text = { Text(snippet.title) },
                            onClick = {
                                codeSnippet = snippet
                                isShowSnippetSelector = false
                            }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { isRepeating = !isRepeating }) {
                    Text(if (isRepeating) "Stop" else "Repeat")
                }

                Button(
                    onClick = { coroutineScope.launch { eval() } },
                    enabled = !isRepeating && !isExecuting
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run code",
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        TextField(
            value = code,
            onValueChange = { code = it },
            modifier = Modifier.fillMaxSize(),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }

    Column(
        modifier = resultModifier.fillMaxHeight()
            .background(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
                shape = MaterialTheme.shapes.medium,
            )
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Output",
                fontWeight = FontWeight.Bold,
            )

            if (executionTime >= 0) {
                Text(text = "${executionTime}ms")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = rememberOutput(logs, result),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun rememberOutput(logs: List<LogItem>, result: Result<String>): AnnotatedString {
    val currentLogs by rememberUpdatedState(logs)
    val currentResult by rememberUpdatedState(result)
    val textColor = MaterialTheme.colorScheme.onBackground
    val errorColor = MaterialTheme.colorScheme.error
    val warnColor = Color(0xffdba034)
    val output by remember {
        derivedStateOf {
            buildAnnotatedString {
                for (log in currentLogs) {
                    val color = when (log.level) {
                        LogLevel.Error -> errorColor
                        LogLevel.Warn -> warnColor
                        LogLevel.Info -> textColor
                    }
                    pushStyle(SpanStyle(color = color))
                    append("[${log.level}]: ")
                    pop()
                    append(log.content)
                    appendLine()
                }

                pushStyle(
                    SpanStyle(
                        color = if (currentResult.isSuccess) textColor else errorColor,
                    )
                )
                append(
                    currentResult.getOrNull()
                        ?: currentResult.exceptionOrNull()!!.stackTraceToString()
                )
                pop()
            }
        }
    }
    return output
}

enum class LogLevel {
    Info,
    Warn,
    Error,
}

data class LogItem(
    val level: LogLevel,
    val content: String,
) {
    companion object {
        fun info(content: String) = LogItem(level = LogLevel.Info, content = content)
        fun warn(content: String) = LogItem(level = LogLevel.Warn, content = content)
        fun error(content: String) = LogItem(level = LogLevel.Error, content = content)
    }
}
