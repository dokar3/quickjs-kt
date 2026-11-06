package com.dokar.quickjs.test

import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RejectionHandledLaterTest {
    /**
     * `call()` returns a promise that rejects one job before the `await` that handles it is
     * attached, so it is briefly unhandled even though the script handles it.
     */
    @Test
    fun rejectionHandledByALaterJobIsCaught() = runTest {
        quickJs {
            val result = evaluate<String>(
                """
                    async function attempt() { throw new Error("refused"); }
                    function logIn() {
                        const attempting = attempt();
                        return attempting.then((won) => won, (error) => { throw error; });
                    }
                    async function call() { return logIn(); }
                    let result = null;
                    try {
                        await call();
                        result = "Resolved";
                    } catch (e) {
                        result = "Caught " + e.message;
                    }
                    result;
                """.trimIndent()
            )
            assertEquals("Caught refused", result)
        }
    }
}
