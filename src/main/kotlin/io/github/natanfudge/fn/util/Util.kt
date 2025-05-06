package io.github.natanfudge.fn.util

fun closeAll(vararg closeable: AutoCloseable) {
    for (closeable in closeable) {
        closeable.close()
    }
}