package com.dokar.quickjs.bridge

import com.dokar.quickjs.qjsError

internal fun circularRefError(): Nothing =
    qjsError("Unable to map objects with circular reference.")