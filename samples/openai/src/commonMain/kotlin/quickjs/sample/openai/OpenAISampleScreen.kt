package quickjs.sample.openai

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.quickJs
import defineEnv
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import quickjs.sample.openai.bindings.Cleanup
import quickjs.sample.openai.bindings.defineFetch
import quickjs.sample.openai.bindings.defineSetTimeout
import quickjs_kt.samples.openai.generated.resources.Res

@Composable
fun OpenAISampleScreen(modifier: Modifier = Modifier) {
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

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun ScreenContent(
    codeModifier: Modifier = Modifier,
    resultModifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    var code by remember {
        mutableStateOf(
            """
                import OpenAI from 'openai';

                const openai = new OpenAI({
                  apiKey: process.env['OPENAI_API_KEY'], // This is the default and can be omitted
                });

                async function main() {
                  const stream = await openai.chat.completions.create({
                    messages: [{ role: 'user', content: 'Say this is a test' }],
                    model: 'gpt-3.5-turbo',
                    stream: true,
                  });
                  for await (const chunk of stream) {
                    if (chunk.choices == null) {
                      console.error(JSON.stringify(chunk));
                    } else {
                      console.warn(chunk.choices[0]?.delta?.content || '');
                    }
                  }
                }

                main();
            """.trimIndent()
        )
    }

    val logs = remember { mutableStateListOf<LogItem>() }

    var result by remember { mutableStateOf(Result.success("")) }

    var executionTime by remember { mutableLongStateOf(-1L) }

    var isExecuting by remember { mutableStateOf(false) }

    suspend fun eval() = withContext(Dispatchers.IO) {
        logs.clear()
        result = Result.success("")
        isExecuting = true
        val start = System.currentTimeMillis()
        result = runCatching {
            quickJs {
                val cleanups = mutableListOf<Cleanup>()

                // process.env
                defineEnv(
                    mapOf(
                        "OPENAI_API_KEY" to "DUMMY_VALUE",
                    )
                )

                var fetchError: Throwable? = null
                val exceptionHandler = CoroutineExceptionHandler { _, error ->
                    fetchError = error
                    close()
                }
                // fetch()
                cleanups += defineFetch(CoroutineScope(Dispatchers.IO + exceptionHandler))

                // setTimeout() / clearTimeout()
                cleanups += defineSetTimeout()

                addModule(
                    name = "openai",
                    code = Res.readBytes("files/openai.js").decodeToString(),
                )

                define("console") {
                    function<Unit>("log") {
                        logs.add(LogItem.debug(it.joinToString(" ")))
                    }

                    function<Unit>("info") {
                        logs.add(LogItem.info(it.joinToString(" ")))
                    }

                    function<Unit>("warn") {
                        logs.add(LogItem.warn(it.joinToString(" ")))
                    }

                    function<Unit>("error") {
                        logs.add(LogItem.error(it.joinToString(" ")))
                    }
                }

                val evalError = try {
                    evaluate<Any?>(
                        code = code,
                        filename = "openai-sample.js",
                        asModule = true,
                    )
                    null
                } catch (e: Throwable) {
                    e
                }

                for (cleanup in cleanups) {
                    cleanup()
                }

                (fetchError ?: evalError)?.let { throw it }

                null
            }.toString()
        }
        val end = System.currentTimeMillis()
        executionTime = end - start
        isExecuting = false
    }

    Column(modifier = codeModifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "OpenAI SDK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { coroutineScope.launch { eval() } },
                    enabled = !isExecuting
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
                        LogLevel.Debug, LogLevel.Info -> textColor
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
    Debug,
    Info,
    Warn,
    Error,
}

data class LogItem(
    val level: LogLevel,
    val content: String,
) {
    companion object {
        fun debug(content: String) = LogItem(level = LogLevel.Debug, content = content)
        fun info(content: String) = LogItem(level = LogLevel.Info, content = content)
        fun warn(content: String) = LogItem(level = LogLevel.Warn, content = content)
        fun error(content: String) = LogItem(level = LogLevel.Error, content = content)
    }
}
