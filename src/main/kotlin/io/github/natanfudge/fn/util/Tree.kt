@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.util

import java.util.LinkedList
import java.util.Queue
import kotlin.collections.ArrayDeque

interface Tree<out T> {
    val value: T
    val children: List<Tree<T>>
}

interface MutableTree<T> : Tree<T> {
    override val children: MutableList<MutableTree<T>>
}

data class MutableTreeImpl<T>(override val value: T, override val children: MutableList<MutableTree<T>>) : MutableTree<T>

/**
 * Represents the path from the root to a certain node, assuming the list of children is accessed by the given [index] at each layer.
 */
data class TreePath(val index: Int, val prev: TreePath?) {
    companion object {
        val Root = TreePath(0, null)
    }

    inline fun visitBottomUp(visitor: (Int, isRoot: Boolean) -> Unit) {
        var current: TreePath? = this
        while (current != null) {
            // isRoot = current.prev == null
            visitor(current.index, current.prev == null)
            current = current.prev
        }
    }

    fun toList(): List<Int> = buildList {
        visitBottomUp { index, isRoot ->
            if (!isRoot) {
                // The root has no index, it is just the thing itself
                add(index)
            }
        }
    }.asReversed()

    override fun toString(): String {
        return toList().toString()
    }

    fun createChild(index: Int) = TreePath(index, this)
}

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
 * Visits the tree breadth-first.
 */
inline fun <T, M :Tree<T>> M.visitSubtrees(visitor: (M) -> Unit) {
    // Neat trick to have a recursion-less tree visitor - populate a queue as you iterate through the tree
    val queue: Queue<M> = LinkedList()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val next = queue.poll()
        queue.addAll(next.children as List<M>)
        visitor(next)
    }
}

/**
 * Visits the tree *bottom-up* (reverse breadth-first, aka reverse level-order), guaranteeing that each distinct node
 * is visited at most once-even if it appears more than once in the hierarchy.
 *
 * Nodes on the same level are still reported left-to-right; only the levels
 * themselves are reversed.  Runs in O(n) time and O(n) extra space.
 */
inline fun <T> Tree<T>.visitBottomUpUnique(visitor: (T) -> Unit) {
    val queue: ArrayDeque<Tree<T>> = ArrayDeque()
    val output: ArrayDeque<T> = ArrayDeque()   // acts as a stack
    val seen = mutableSetOf<T>()

    queue.add(this)
    seen.add(this.value)
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()

        // Collect the value; we'll emit later in reverse.
        output.addFirst(node.value)

        // Enqueue children *after* the push so they’re processed before
        // their parent when we later pop from 'output'.
        // Children go in natural order so left-to-right is preserved.
        for (child in node.children) {
            // Enqueue children only if we haven’t met them before.
            if (seen.add(child.value)) {        // true ↔ it wasn’t there yet
                queue.add(child)
            }
        }
    }

    // Now emit bottom-up.
    for (value in output) visitor(value)
}

/**
 * Visits each node in the tree depth-first, providing its parent in the process. For the root, the [seed] value will be given as the parent.
 */
fun <T> Tree<T>.visitConnections(seed: T, visitor: (parent: T, child: T) -> Unit) {
    visitConnectionsRecur(this, seed, visitor)
}

private fun <T> visitConnectionsRecur(node: Tree<T>, parentValue: T, visitor: (parent: T, child: T) -> Unit) {
    visitor(parentValue, node.value)

    for (child in node.children) {
        visitConnectionsRecur(child, node.value, visitor)
    }
}


