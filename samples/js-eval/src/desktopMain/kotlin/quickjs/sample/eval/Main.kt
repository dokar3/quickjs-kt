package quickjs.sample.eval

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import quickjs.sample.eval.EvalScreen

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "js-eval") {
        EvalScreen()
    }
}
