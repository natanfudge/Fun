package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.util.EventStream

interface FunResource {
    val context get() = FunContextRegistry.getContext()
    fun alsoClose(closeable: AutoCloseable)

    val events get() = context.events
}

fun <T> EventStream<T>.listen(resource: FunResource, callback: (T) -> Unit) {
    val listener = listenUnscoped("old", callback)
    resource.alsoClose(listener)
}

context(resource: FunResource)
fun <T> EventStream<T>.listen(callback: (T) -> Unit) {
    val listener = listenUnscoped("old", callback)
    resource.alsoClose(listener)
}