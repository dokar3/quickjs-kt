package com.dokar.quickjs.util

import kotlinx.coroutines.sync.Mutex

internal inline fun <T> Mutex.withLockSync(
    block: () -> T,
): T {
    try {
        while (!this.tryLock()) {
            // Loop until the lock is available, giving the holder a chance to
            // finish instead of burning a whole core while we wait.
            yieldThread()
        }
        return block()
    } finally {
        this.unlock()
    }
}

internal expect fun yieldThread()
