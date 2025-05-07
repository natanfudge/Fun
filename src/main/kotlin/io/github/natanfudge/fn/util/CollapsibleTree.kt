package io.github.natanfudge.fn.util

interface CollapsibleTree<T> : Tree<T> {
    /**
     * Whether this node has children, or additional children that are hidden
     */
    val hasContent: Boolean
}

val <T> Tree<T>.hasContent get() = if(this is CollapsibleTree<T>) hasContent else children.isNotEmpty()