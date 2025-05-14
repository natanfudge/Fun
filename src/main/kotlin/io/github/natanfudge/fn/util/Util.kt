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