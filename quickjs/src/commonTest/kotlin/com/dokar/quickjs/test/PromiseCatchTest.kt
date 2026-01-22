package com.dokar.quickjs.test

import com.dokar.quickjs.quickJs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PromiseCatchTest {
    @Test
    fun testPromiseCatchShouldNotThrow() = runTest {
        quickJs {
            // This should NOT throw - the error is caught by .catch()
            val result = evaluate<String>("""
                async function fetch() { throw Error("bad thing happened"); }; 
                await fetch().catch((e) => {}); 
                'Caught';
            """.trimIndent())
            assertEquals("Caught", result)
        }
    }

    @Test
    fun testPromiseCatchWithErrorHandler() = runTest {
        quickJs {
            // This should NOT throw - the error is caught by .catch()
            val result = evaluate<String>("""
                async function doSomething() { 
                    throw Error("test error"); 
                }
                
                let errorMessage = null;
                await doSomething().catch((e) => { 
                    errorMessage = e.message; 
                });
                errorMessage;
            """.trimIndent())
            assertEquals("test error", result)
        }
    }
}
