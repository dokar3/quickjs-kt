package com.dokar.quickjs.util

import kotlinx.coroutines.sync.Mutex

internal inline fun Mutex.withLockSync(
    block: () -> Unit,
) {
    try {
        while (!this.tryLock()) {
            // Loop until the lock is available
        }
        block()
    } finally {
        this.unlock()
    }
}