package quickjs.sample.eval

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "js-eval") {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface {
                EvalScreen()
            }
        }
    }
}
