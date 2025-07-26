package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.util.EventEmitter

fun <T> NewFun.memo(key: String, dependencies: CacheDependencyKeys, ctr: () -> T): T {
    val key = "$id[$key]"
    return context.cache.getOrCreate(key, dependencies, ctr)
}

internal fun checkListenersClosed(events: NewFunEvents)  = with(events){
    checkClosed(beforeFrame, frame, afterFrame, beforePhysics, physics, afterPhysics, input, guiError, appClosed)
}

private fun checkClosed(vararg events: EventEmitter<*>) {
    for (event in events) {
        check(!event.hasListeners)
    }
}