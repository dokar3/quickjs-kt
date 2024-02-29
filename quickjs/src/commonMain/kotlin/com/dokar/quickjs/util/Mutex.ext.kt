package com.dokar.quickjs.util

import kotlinx.coroutines.sync.Mutex

internal inline fun <T> Mutex.withLockSync(
    block: () -> T,
): T {
    try {
        while (!this.tryLock()) {
            // Loop until the lock is available
        }
        return block()
    } finally {
        this.unlock()
    }
}