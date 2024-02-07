package com.dokar.quickjs

/**
 * QuickJS runtime memory usage.
 */
class MemoryUsage(
    val mallocLimit: Long,
    val mallocSize: Long,
    val mallocCount: Long,
    val memoryUsedSize: Long,
    val memoryUsedCount: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryUsage) return false

        if (mallocLimit != other.mallocLimit) return false
        if (mallocSize != other.mallocSize) return false
        if (mallocCount != other.mallocCount) return false
        if (memoryUsedSize != other.memoryUsedSize) return false
        if (memoryUsedCount != other.memoryUsedCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mallocLimit.hashCode()
        result = 31 * result + mallocSize.hashCode()
        result = 31 * result + mallocCount.hashCode()
        result = 31 * result + memoryUsedSize.hashCode()
        result = 31 * result + memoryUsedCount.hashCode()
        return result
    }

    override fun toString(): String {
        return "MemoryUsage(mallocLimit=$mallocLimit, " +
                "mallocSize=$mallocSize, " +
                "mallocCount=$mallocCount, " +
                "memoryUsedSize=$memoryUsedSize, " +
                "memoryUsedCount=$memoryUsedCount)"
    }
}