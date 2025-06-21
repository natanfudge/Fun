package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.network.Fun
import java.util.LinkedList

/**
 * Returns the root [Fun] node of the render [Fun] that is currently being hovered by the mouse.
 */
fun FunContext.getHoveredRoot(): Fun? {
    var current = world.hoveredObject as? Fun ?: return null
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