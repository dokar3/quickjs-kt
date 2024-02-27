import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define

/**
 * Define env variables on `process.env`.
 */
internal fun QuickJs.defineEnv(map: Map<String, String>) {
    define("process") {
        define("env") {
            for (env in map) {
                property(env.key) {
                    getter { env.value }
                }
            }
        }
    }
}