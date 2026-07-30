package com.dokar.quickjs.util

import platform.posix.sched_yield

internal actual fun yieldThread() {
    sched_yield()
}
