package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.Fun
import java.util.LinkedList

/**
 * Returns the root [Fun] node of the render [Fun] that is currently being hovered by the mouse.
 */
fun FunContext.getHoveredParent(): Fun? {
    val directHovered = world.hoveredObject as? Fun ?: return null
    return directHovered.parent
}

fun Fun.getRoot() : Fun {
    var current = this
    while (true) {
        val parent = current.parent
        if (parent == null) return current
        current = parent
    }
}

inline fun <reified T> Fun.forEachChildTyped(iter: (T) -> Unit) {
    val queue = LinkedList<Fun>()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current is T) iter(current)
        queue.addAll(current.children)
    }
}

inline fun <reified T> Fun.childrenTyped(): List<T>  = buildList {
    forEachChildTyped<T> { add(it) }
}