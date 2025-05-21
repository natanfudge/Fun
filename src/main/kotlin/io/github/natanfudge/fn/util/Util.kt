package io.github.natanfudge.fn.util

fun closeAll(vararg closeables: AutoCloseable) {
    for (closeable in closeables) {
        // On reload the values might become null
        @Suppress("UNNECESSARY_SAFE_CALL")
        closeable?.close()
    }
}

inline fun <T> Iterable<T>.allIndexed(iter: (Int, T) -> Boolean): Boolean {
    forEachIndexed { i, el ->
        val result = iter(i, el)
        if (!result) return false
    }
    return true
}

fun concatArrays(array1: FloatArray, array2: FloatArray, array3: FloatArray): FloatArray {
    val res = FloatArray(array1.size + array2.size + array3.size)
    array1.copyInto(res)
    array2.copyInto(res, array1.size)
    array3.copyInto(res, array1.size + array2.size)
    return res
}


fun concatArrays(array1: FloatArray, array2: FloatArray, array3: FloatArray, array4: FloatArray): FloatArray {
    val res = FloatArray(array1.size + array2.size + array3.size + array4.size)
    array1.copyInto(res)
    array2.copyInto(res, array1.size)
    array3.copyInto(res, array1.size + array2.size)
    array4.copyInto(res, array1.size + array2.size + array3.size)
    return res
}

