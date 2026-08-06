import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickJsIntegrationTest {
    @Test
    fun evalExpression() = runTest {
        quickJs {
            val result = evaluate<Int>("1 + 2")
            assertEquals(3, result)
        }
    }

    @Test
    fun evalString() = runTest {
        quickJs {
            val result = evaluate<String>("'hello' + ' ' + 'world'")
            assertEquals("hello world", result)
        }
    }
}
