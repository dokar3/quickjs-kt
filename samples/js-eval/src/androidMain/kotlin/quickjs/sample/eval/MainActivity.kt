package quickjs.sample.eval

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import quickjs.sample.eval.EvalScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EvalScreen()
        }
    }
}