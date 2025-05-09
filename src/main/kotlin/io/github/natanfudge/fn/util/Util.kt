package io.github.natanfudge.fn.util

fun closeAll(vararg closeable: AutoCloseable) {
    for (closeable in closeable) {
        closeable.close()
    }
}

inline fun <T> Iterable<T>.allIndexed(iter: (Int, T) -> Boolean): Boolean {
    forEachIndexed { i, el ->
        val result = iter(i, el)
        if (!result) return false
    }
    return true
}