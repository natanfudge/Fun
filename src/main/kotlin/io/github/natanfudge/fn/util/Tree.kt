@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.util

import java.util.LinkedList
import java.util.Queue
import kotlin.collections.ArrayDeque

interface Tree<out T> {
    val value: T
    val children: List<Tree<T>>
}


/*
 * Helper records
 * ──────────────
 */
data class NodeParent<T>(
    /** parent.value                      */ val value: T,
    /** index of the child in parent.children */ val childIndex: Int
)

/** A node plus *all* of its parents (root ⇒ parents == emptyList()) */
data class TreeEdge<T>(
    val parents: List<NodeParent<T>>,
    val child: T
)

private fun <T>dfs(node: Tree<T>, allNodes: MutableSet<Tree<T>>, parents: MutableMap<Tree<T>,MutableList<NodeParent<T>>>) {
    if (!allNodes.add(node)) return            // already seen
    node.children.forEachIndexed { idx, child ->
        parents.getOrPut(child) { mutableListOf() }
            .add(NodeParent(node.value, idx))
        dfs(child, allNodes, parents)                             // recurse once per child
    }
}

/** Returns each reachable node exactly once in parent-before-child order. */
fun <T> Tree<T>.topologicalSort(): List<TreeEdge<T>> {
    /* STEP 1 – DFS reachability & parent collection */
    val allNodes = mutableSetOf<Tree<T>>()
    val parents  = mutableMapOf<Tree<T>, MutableList<NodeParent<T>>>()


    dfs(this, allNodes, parents)                                     // start from the receiver

    /* STEP 2 – in-degree table from the parents map */
    val inDeg = mutableMapOf<Tree<T>, Int>().withDefault { 0 }
    parents.forEach { (child, plist) -> inDeg[child] = plist.size }
    allNodes.forEach { inDeg.putIfAbsent(it, 0) } // roots default to 0

    /* STEP 3 – Kahn’s queue seeded with every zero-in-degree node */
    val queue  = ArrayDeque(allNodes.filter { inDeg.getValue(it) == 0 })
    val result = mutableListOf<TreeEdge<T>>()

    /* STEP 4 – main loop */
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        result += TreeEdge(parents[node].orEmpty(), node.value)

        node.children.forEach { child ->
            val d = inDeg.getValue(child) - 1
            inDeg[child] = d
            if (d == 0) queue.add(child)
        }
    }

    /* STEP 5 – sanity check */
    check(result.size == allNodes.size) { "Graph contains a cycle" }
    return result
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
fun <T> Tree<T>.visitConnections(visitor: (parent: T?, child: T) -> Unit) {
    visitConnectionsRecur(this, null, visitor)
}

private fun <T> visitConnectionsRecur(node: Tree<T>, parentValue: T?, visitor: (parent: T?, child: T) -> Unit) {
    visitor(parentValue, node.value)

    for (child in node.children) {
        visitConnectionsRecur(child, node.value, visitor)
    }
}

data class Edge<T>(val parent: T, val child: T)

fun <T> Tree<T>.collectEdges(): List<Edge<T>> = buildList {
    visitConnections {parent, child ->
        if(parent != null) {
            add(Edge(parent ,child))
        }
    }
//    visitEdgePaths(depth) {
//        add(it)
//    }
}
