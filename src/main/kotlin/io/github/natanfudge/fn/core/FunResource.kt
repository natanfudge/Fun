package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.util.EventStream



fun <T> EventStream<T>.listen(resource: Resource, callback: (T) -> Unit) {
    val listener = listenUnscoped("Unnamed Listener", callback)
    resource.alsoClose(listener)
}

