package io.github.natanfudge.fn.compose.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.natanfudge.fn.util.*
import java.util.*


data class CollectedVertex<T>(val value: T, val path: TreePath, val hasContent: Boolean)

/**
 * Returns all nodes of the tree, by layer, as well as the path to each node.
 */
fun <T> Tree<T>.collectLayers(): List<List<CollectedVertex<T>>> {
    var row = listOf(this to TreePath.Root)
    val result = mutableListOf<List<CollectedVertex<T>>>()
    while (row.isNotEmpty()) {
        result.add(row.mapIndexed { i, (node, path) ->
            CollectedVertex(node.value, path, node.hasContent)
        })
        row = row.flatMap { (layerNode, path) ->
            layerNode.children.mapIndexed { i, child ->
                child to path.createChild(i)
            }
        }
    }

    return result
}


data class LabeledConnection<T, L>(
    val label: L,
    val value: T,
)

//data class LabeledEdgeTreeIter<T, L>(
//    val tree: LabeledEdgeTree<T, L>,
//    val depth: Int,
//)

data class LabeledEdgeTreePathIter<T, L>(
    val tree: LabeledEdgeTree<T, L>,
    val path: TreePath,
    val depth: Int,
)

data class LabeledEdge<T, L>(
    val parentPath: TreePath,
//    val parent: T,
    val label: L?,
    /**
     * How many children the parent has
     */
    val totalBrotherCount: Int,
    val childPath: TreePath,
//    val child: T,
)

inline fun <T> Tree<T>.find(predicate: (T) -> Boolean): T?  = findNode(predicate)?.value

inline fun <T> Tree<T>.findNode(predicate: (T) -> Boolean): Tree<T>? {
    visitNodes { // Possible because of awesome recursionless inline visit algorithm
        if (predicate(it.value)) return it
    }
    return null
}



/**
 * Traverses the tree, going to a specific child denoted by [travelDirections].
 * If [travelDirections] returns null, it means the desired node has been found and it will be returned.
 * This function assumes the thing can be found every time, not returning null ever.
 */
fun <T> Tree<T>.findDirected(travelDirections: (T, children: List<Tree<T>>) -> Int?): T {
    val child = travelDirections(value, children)
    if (child == null) return value
    else {
        check(children.isNotEmpty()) { "Could not find matching node" }
        require(child in children.indices) {
            "Given child index $child, but only ${children.size} children exist"
        }
        return children[child].findDirected(travelDirections)
    }
}


fun <T> Tree<T>.toList() = buildList { visit { add(it) } }


data class TreeImpl<T>(override val value: T, override val children: List<Tree<T>>) : Tree<T> {

    override fun toString(): String = buildString {
        appendNode(this@TreeImpl, 0)
    }

    // ---- helpers ----
    private fun StringBuilder.appendNode(node: Tree<T>, depth: Int) {
        repeat(depth) { append("  ") }        // 2-space indent per level
        append(node.value)

        if (node.children.isNotEmpty()) {
            append('\n')
            node.children.forEachIndexed { idx, child ->
                appendNode(child, depth + 1)
                if (idx != node.children.lastIndex) append('\n')
            }
        }
    }
}

data class LabeledEdgeTree<T, L>(val tree: Tree<T>, val labels: TreeEdgeLabeler<T, L>)

fun interface TreeEdgeLabeler<N, L> {
    fun labelNodeEdges(node: N): List<L>
}

fun <T, L> LabeledEdgeTree<T, L>.collectEdgePaths(depth: Int): List<LabeledEdge<T, L>> = buildList {
    visitEdgePaths(depth) {
        add(it)
    }
}


//inline fun <T, L> LabeledEdgeTree<T, L>.visitChildrenPaths(
//    depth: Int,
//    visitor: (parentPath: TreePath, causingChoice: L, childPath: TreePath) -> Unit,
//) {
//    val queue: Queue<LabeledEdgeTreeIter2<T, L>> = LinkedList() // We need to track the depth to know when to stop
//    queue.add(LabeledEdgeTreeIter2(this, depth = 0))
//
//    while (queue.isNotEmpty()) {
//        val next = queue.poll()
//        // Don't go further than requested depth
//        if (next.depth + 1 <= depth) {
//            val nextTree = next.tree.tree
//            val labels = next.tree.labels.labelNodeEdges(nextTree.value)
//            nextTree.children.zip(labels).map { (node, label) ->
//                visitor(nextTree.value, label, node.value)
//                queue.add(LabeledEdgeTreeIter2(next.tree.copy(tree = node), next.depth + 1))
//            }
//        }
//
//    }
//}

inline fun <T, L> LabeledEdgeTree<T, L>.visitEdgePaths(
    depth: Int,
    visitor: (LabeledEdge<T, L>) -> Unit,
) {

    val queue: Queue<LabeledEdgeTreePathIter<T, L>> = LinkedList() // We need to track the depth to know when to stop
    queue.add(LabeledEdgeTreePathIter(this, path = TreePath.Root, depth = 0))

    while (queue.isNotEmpty()) {
        val next = queue.poll()
        // Don't go further than requested depth
        if (next.depth + 1 <= depth) {
            val nextTree = next.tree.tree
            val labels = next.tree.labels.labelNodeEdges(nextTree.value)
            nextTree.children.forEachIndexed { i, node ->
                val label = labels.getOrNull(i)
                val childPath = next.path.createChild(i)

                visitor(
                    LabeledEdge(
                        parentPath = next.path, label = label, childPath = childPath,
                        totalBrotherCount = nextTree.children.size
                    )
                )
                queue.add(LabeledEdgeTreePathIter(next.tree.copy(tree = node), childPath, next.depth + 1))
            }
        }

    }
}


fun <T> Tree<T>.toPartial(initiallyShowChildren: Boolean): PartialTree<T> {
    return PartialTree(
        value, children.map { it.toPartial(initiallyShowChildren) }, initiallyShowChildren
    )
}

/**
 * [Tree] that can be modified to show more or less of its nodes.
 */
class PartialTree<T>(
    override val value: T,
    private val _children: List<PartialTree<T>>,
    initiallyShowChildren: Boolean,
) : CollapsibleTree<T> {
    private var showChildren by mutableStateOf(initiallyShowChildren)

    override val hasContent: Boolean = _children.isNotEmpty()

    /**
     * Will turn off / turn on the children of the node in the given [path].
     */
    fun toggleShow(path: TreePath) {
        var current = this
        for (index in path.toList()) {
            current = current._children[index]
        }
        current.showChildren = !current.showChildren
    }

    override val children: List<Tree<T>>
        get() = if (showChildren) _children else listOf()
}

