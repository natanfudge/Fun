package io.github.natanfudge.fn.test

import io.github.natanfudge.fn.util.MutableTreeImpl
import io.github.natanfudge.fn.util.visit
import io.github.natanfudge.fn.util.visitConnections
import kotlin.test.Test
import kotlin.test.assertEquals

class TreeVisitTest {

    /**
     *  Tree shape:
     *
     *          "root"
     *         /      \
     *     "L1-A"   "L1-B"
     *      /   \        \
     *  "L2-A" "L2-B"  "L2-C"
     *
     * Expected breadth-first order: root, L1-A, L1-B, L2-A, L2-B, L2-C
     */
    @Test
    fun visit_traversesBreadthFirst() {
        // Build the tree bottom-up
        val l2A = MutableTreeImpl("L2-A", mutableListOf())
        val l2B = MutableTreeImpl("L2-B", mutableListOf())
        val l2C = MutableTreeImpl("L2-C", mutableListOf())

        val l1A = MutableTreeImpl("L1-A", mutableListOf(l2A, l2B))
        val l1B = MutableTreeImpl("L1-B", mutableListOf(l2C))

        val root = MutableTreeImpl("root", mutableListOf(l1A, l1B))

        // Collect the order produced by visit()
        val visited = mutableListOf<String>()
        root.visit { visited += it }

        assertEquals(
            listOf("root", "L1-A", "L1-B", "L2-A", "L2-B", "L2-C"),
            visited,
            "visit() should perform a breadth-first traversal and hit every node exactly once"
        )
    }
}
//    /**
//     * Tree being built:
//     *
//     *          "root"
//     *         /      \
//     *     "L1-A"   "L1-B"
//     *      /   \        \
//     *  "L2-A" "L2-B"  "L2-C"
//     *
//     * Recursive visitConnections() (pre-order DFS) should emit edges in
//     * this exact sequence:
//     *
//     *   (SEED → root)
//     *   (root → L1-A)
//     *   (L1-A → L2-A)
//     *   (L1-A → L2-B)
//     *   (root → L1-B)
//     *   (L1-B → L2-C)
//     */
//    @Test
//    fun visitConnections_reportsCorrectParentChildPairsInDepthFirstOrder() {
//        // Build tree bottom-up
//        val l2A = MutableTreeImpl("L2-A", mutableListOf())
//        val l2B = MutableTreeImpl("L2-B", mutableListOf())
//        val l2C = MutableTreeImpl("L2-C", mutableListOf())
//
//        val l1A = MutableTreeImpl("L1-A", mutableListOf(l2A, l2B))
//        val l1B = MutableTreeImpl("L1-B", mutableListOf(l2C))
//
//        val root = MutableTreeImpl("root", mutableListOf(l1A, l1B))
//
//        // Collect (parent, child) edges
//        val edges = mutableListOf<Pair<String, String>>()
//        val seed = "SEED"
//        root.visitConnections(seed) { parent, child ->
//            edges += parent to child
//        }
//
//        // Expected DFS pre-order edge list
//        val expected = listOf(
//            seed to "root",
//            "root" to "L1-A",
//            "L1-A" to "L2-A",
//            "L1-A" to "L2-B",
//            "root" to "L1-B",
//            "L1-B" to "L2-C",
//        )
//
//        assertEquals(
//            expected,
//            edges,
//            "visitConnections() should visit every edge exactly once " +
//                    "and in deterministic depth-first order"
//        )
//    }
//}