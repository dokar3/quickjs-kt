package com.dokar.quickjs

/**
 * QuickJS runtime memory usage.
 */
class MemoryUsage(
    val mallocLimit: Long,
    val mallocCount: Long,
    val mallocSize: Long,
    val memoryUsedCount: Long,
    val memoryUsedSize: Long,
    val atomCount: Long,
    val atomSize: Long,
    val strCount: Long,
    val strSize: Long,
    val objCount: Long,
    val objSize: Long,
    val propCount: Long,
    val propSize: Long,
    val shapeCount: Long,
    val shapeSize: Long,
    val jsFuncCount: Long,
    val jsFuncSize: Long,
    val jsFuncCodeSize: Long,
    val jsFuncPc2lineCount: Long,
    val jsFuncPc2lineSize: Long,
    val cFuncCount: Long,
    val arrayCount: Long,
    val fastArrayCount: Long,
    val fastArrayElements: Long,
    val binaryObjectCount: Long,
    val binaryObjectSize: Long,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryUsage) return false

        if (mallocLimit != other.mallocLimit) return false
        if (mallocCount != other.mallocCount) return false
        if (mallocSize != other.mallocSize) return false
        if (memoryUsedCount != other.memoryUsedCount) return false
        if (memoryUsedSize != other.memoryUsedSize) return false
        if (atomCount != other.atomCount) return false
        if (atomSize != other.atomSize) return false
        if (strCount != other.strCount) return false
        if (strSize != other.strSize) return false
        if (objCount != other.objCount) return false
        if (objSize != other.objSize) return false
        if (propCount != other.propCount) return false
        if (propSize != other.propSize) return false
        if (shapeCount != other.shapeCount) return false
        if (shapeSize != other.shapeSize) return false
        if (jsFuncCount != other.jsFuncCount) return false
        if (jsFuncSize != other.jsFuncSize) return false
        if (jsFuncCodeSize != other.jsFuncCodeSize) return false
        if (jsFuncPc2lineCount != other.jsFuncPc2lineCount) return false
        if (jsFuncPc2lineSize != other.jsFuncPc2lineSize) return false
        if (cFuncCount != other.cFuncCount) return false
        if (arrayCount != other.arrayCount) return false
        if (fastArrayCount != other.fastArrayCount) return false
        if (fastArrayElements != other.fastArrayElements) return false
        if (binaryObjectCount != other.binaryObjectCount) return false
        if (binaryObjectSize != other.binaryObjectSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mallocLimit.hashCode()
        result = 31 * result + mallocCount.hashCode()
        result = 31 * result + mallocSize.hashCode()
        result = 31 * result + memoryUsedCount.hashCode()
        result = 31 * result + memoryUsedSize.hashCode()
        result = 31 * result + atomCount.hashCode()
        result = 31 * result + atomSize.hashCode()
        result = 31 * result + strCount.hashCode()
        result = 31 * result + strSize.hashCode()
        result = 31 * result + objCount.hashCode()
        result = 31 * result + objSize.hashCode()
        result = 31 * result + propCount.hashCode()
        result = 31 * result + propSize.hashCode()
        result = 31 * result + shapeCount.hashCode()
        result = 31 * result + shapeSize.hashCode()
        result = 31 * result + jsFuncCount.hashCode()
        result = 31 * result + jsFuncSize.hashCode()
        result = 31 * result + jsFuncCodeSize.hashCode()
        result = 31 * result + jsFuncPc2lineCount.hashCode()
        result = 31 * result + jsFuncPc2lineSize.hashCode()
        result = 31 * result + cFuncCount.hashCode()
        result = 31 * result + arrayCount.hashCode()
        result = 31 * result + fastArrayCount.hashCode()
        result = 31 * result + fastArrayElements.hashCode()
        result = 31 * result + binaryObjectCount.hashCode()
        result = 31 * result + binaryObjectSize.hashCode()
        return result
    }

    override fun toString(): String {
        return "MemoryUsage(mallocLimit=$mallocLimit, " +
                "mallocCount=$mallocCount, " +
                "mallocSize=$mallocSize, " +
                "memoryUsedCount=$memoryUsedCount, " +
                "memoryUsedSize=$memoryUsedSize, " +
                "atomCount=$atomCount, " +
                "atomSize=$atomSize, " +
                "strCount=$strCount, " +
                "strSize=$strSize, " +
                "objCount=$objCount, " +
                "objSize=$objSize, " +
                "propCount=$propCount, " +
                "propSize=$propSize, " +
                "shapeCount=$shapeCount, " +
                "shapeSize=$shapeSize, " +
                "jsFuncCount=$jsFuncCount, " +
                "jsFuncSize=$jsFuncSize, " +
                "jsFuncCodeSize=$jsFuncCodeSize, " +
                "jsFuncPc2lineCount=$jsFuncPc2lineCount, " +
                "jsFuncPc2lineSize=$jsFuncPc2lineSize, " +
                "cFuncCount=$cFuncCount, " +
                "arrayCount=$arrayCount, " +
                "fastArrayCount=$fastArrayCount, " +
                "fastArrayElements=$fastArrayElements, " +
                "binaryObjectCount=$binaryObjectCount, " +
                "binaryObjectSize=$binaryObjectSize)"
    }
}