package io.github.natanfudge.fn.util

import java.util.LinkedList
import java.util.Queue

interface Tree<T> {
    val value: T
    val children: List<Tree<T>>
}

interface MutableTree<T> : Tree<T> {
   override val children: MutableList<MutableTree<T>>
}

data class MutableTreeImpl<T>(override val value: T, override val children: MutableList<MutableTree<T>>) : MutableTree<T>

/**
 * Visits the tree breadth-first.
 */
inline fun <T> Tree<T>.visit(visitor: (T) -> Unit) {
    // Neat trick to have a recursion-less tree visitor - populate a queue as you iterate through the tree
    val queue: Queue<Tree<T>> = LinkedList()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val next = queue.poll()
        queue.addAll(next.children)
        visitor(next.value)
    }
}

/**
 * Visits each node in the tree depth-first, providing its parent in the process. For the root, the [seed] value will be given as the parent.
 */
 fun <T> Tree<T>.visitConnections(seed: T, visitor: (parent: T, child:T) -> Unit) {
    visitConnectionsRecur(this, seed, visitor)
}

private fun <T> visitConnectionsRecur(node: Tree<T>, parentValue: T, visitor: (parent: T, child:T) -> Unit) {
    visitor(parentValue, node.value)

    for (child in node.children) {
        visitConnectionsRecur(child, node.value, visitor)
    }
}

