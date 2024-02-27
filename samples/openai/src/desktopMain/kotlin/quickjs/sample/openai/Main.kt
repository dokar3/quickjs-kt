package quickjs.sample.openai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "openai-sdk") {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface {
                OpenAISampleScreen()
            }
        }
    }
}
