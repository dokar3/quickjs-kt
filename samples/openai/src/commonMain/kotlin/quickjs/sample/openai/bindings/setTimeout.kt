package quickjs.sample.openai.bindings

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

internal suspend fun QuickJs.defineSetTimeout(): Cleanup {
    var delayId = 1L
    val delays = mutableMapOf<Long, Job>()

    val cleanup: Cleanup = {
        for (job in delays.values) {
            job.cancel()
        }
    }

    function("_nextDelayId") { delayId++ }

    function("_cancelDelayInternal") { args ->
        delays.remove(args.first() as Long)?.cancel()
    }

    asyncFunction("_delayInternal") { args ->
        val millis = args.first() as Long
        val id = args[1] as Long
        coroutineScope {
            val job = async { delay(millis) }
            delays[id] = job
            // Will throw if job is canceled
            job.await()
        }
    }

    evaluate<Any?>(
        """
        function setTimeout(callback, timeout) {
            const id = _nextDelayId();
            _delayInternal(timeout, id)
                .then(() =>  callback())
                .catch((e) => {});
            return id;
        }
        
        function clearTimeout(id) {
            _cancelDelayInternal(id)
        }
        """.trimIndent(),
        filename = "setTimeout.js"
    )

    return cleanup
}
