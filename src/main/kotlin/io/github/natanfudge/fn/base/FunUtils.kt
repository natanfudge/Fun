package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunOld
import java.util.LinkedList

/**
 * Returns the root [FunOld] node of the render [FunOld] that is currently being hovered by the mouse.
 */
fun FunContext.getHoveredRoot(): FunOld? {
    val directHovered = world.hoveredObject as? FunOld ?: return null
    return directHovered.getRoot()
}

fun FunOld.getRoot() : FunOld {
    var current = this
    while (true) {
        val parent = current.parent
        if (parent == null) return current
        current = parent
    }
}

inline fun <reified T> FunOld.forEachChildTyped(iter: (T) -> Unit) {
    val queue = LinkedList<FunOld>()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current is T) iter(current)
        queue.addAll(current.children)
    }
}

inline fun <reified T> FunOld.childrenTyped(): List<T>  = buildList {
    forEachChildTyped<T> { add(it) }
}