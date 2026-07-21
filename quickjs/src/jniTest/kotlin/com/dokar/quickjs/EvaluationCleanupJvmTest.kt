package com.dokar.quickjs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class EvaluationCleanupJvmTest {
    @Test
    fun cancelledEvaluationReleasesResultHandleBeforeRegistration() = runTest {
        val quickJs = QuickJs.create(StandardTestDispatcher(testScheduler))
        try {
            val mutexLocked = CompletableDeferred<Unit>()
            val releaseMutex = CompletableDeferred<Unit>()
            val mutexHolder = launch {
                quickJs.jobsMutex().withLock {
                    mutexLocked.complete(Unit)
                    releaseMutex.await()
                }
            }
            mutexLocked.await()

            val cancelledEvaluation = launch {
                quickJs.evaluate<Any?>("await new Promise(() => {})")
            }
            runCurrent()
            assertFalse(cancelledEvaluation.isCompleted)

            cancelledEvaluation.cancel()
            runCurrent()
            assertFalse(cancelledEvaluation.isCompleted)

            releaseMutex.complete(Unit)
            mutexHolder.join()
            cancelledEvaluation.join()

            val nextEvaluation = launch {
                quickJs.evaluate<Any?>("await new Promise(() => {})")
            }
            runCurrent()
            assertEquals(setOf(0L), quickJs.activeEvaluateResults())

            nextEvaluation.cancel()
            nextEvaluation.join()
        } finally {
            quickJs.close()
        }
    }

    @Test
    fun cancelledEvaluationReleasesResultHandleWhenJobsMutexIsContended() = runTest {
        val quickJs = QuickJs.create(StandardTestDispatcher(testScheduler))
        try {
            val evaluation = launch {
                quickJs.evaluate<Any?>("await new Promise(() => {})")
            }
            runCurrent()
            assertEquals(1, quickJs.activeEvaluateResults().size)

            val mutexLocked = CompletableDeferred<Unit>()
            val releaseMutex = CompletableDeferred<Unit>()
            val mutexHolder = launch {
                quickJs.jobsMutex().withLock {
                    mutexLocked.complete(Unit)
                    releaseMutex.await()
                }
            }
            mutexLocked.await()

            evaluation.cancel()
            runCurrent()
            assertFalse(evaluation.isCompleted)

            releaseMutex.complete(Unit)
            mutexHolder.join()
            evaluation.join()

            assertEquals(0, quickJs.activeEvaluateResults().size)
        } finally {
            quickJs.close()
        }
    }

    private fun QuickJs.jobsMutex(): Mutex {
        return javaClass.getDeclaredField("jobsMutex").let { field ->
            field.isAccessible = true
            field.get(this) as Mutex
        }
    }

    private fun QuickJs.activeEvaluateResults(): Set<Long> {
        return javaClass.getDeclaredField("activeEvaluateResults").let { field ->
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(this) as Set<Long>).toSet()
        }
    }
}
