package io.github.natanfudge.fn.test.util// TopologicalSortTest.kt
import io.github.natanfudge.fn.util.NodeParent
import io.github.natanfudge.fn.util.Tree
import io.github.natanfudge.fn.util.TreeEdge
import io.github.natanfudge.fn.util.topologicalSort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Simple immutable node implementation just for tests. */
private data class SimpleTree<T>(
    override val value: T,
    override val children: List<Tree<T>> = emptyList()
) : Tree<T>

/** Helper: make child-to-index map once for fast look-ups. */
private fun <T> edgeIndexMap(edges: List<TreeEdge<T>>) =
    edges.mapIndexed { i, e -> e.child to i }.toMap()

/** Helper: generic correctness checks shared by all tests. */
private fun <T> verifyTopologicalCorrectness(edges: List<TreeEdge<T>>) {
    val index = edgeIndexMap(edges)

    // 1. Every parent appears before its child
    edges.forEach { edge ->
        edge.parents.forEach { parent ->
            val parentPos = index[parent.value]
                ?: error("Parent ${parent.value} missing from result list")
            val childPos = index[edge.child]!!
            assertTrue(
                parentPos < childPos,
                "Parent ${parent.value} (idx $parentPos) should precede child ${edge.child} (idx $childPos)"
            )
        }
    }

    // 2. Each node appears exactly once as child
    assertEquals(
        index.size, edges.size,
        "Some node appears twice or is missing in result"
    )
}

/*───────────────────────────  TESTS  ───────────────────────────*/

class TopologicalSortTest {

    @Test
    fun `single root node`() {
        val root = SimpleTree("root")
        val edges = root.topologicalSort()

        assertEquals(1, edges.size)
        assertEquals("root", edges.single().child)
        assertTrue(edges.single().parents.isEmpty())

        verifyTopologicalCorrectness(edges)
    }

    @Test
    fun `simple two-level tree`() {
        val a    = SimpleTree("a")
        val b    = SimpleTree("b")
        val root = SimpleTree("root", listOf(a, b))

        val edges = root.topologicalSort()
        assertEquals(3, edges.size)

        val aEdge = edges.first { it.child == "a" }
        assertEquals(listOf(NodeParent("root", 0)), aEdge.parents)

        val bEdge = edges.first { it.child == "b" }
        assertEquals(listOf(NodeParent("root", 1)), bEdge.parents)

        verifyTopologicalCorrectness(edges)
    }

    @Test
    fun `diamond graph with shared child`() {
        val c    = SimpleTree("c")
        val a    = SimpleTree("a", listOf(c))   // a → c
        val b    = SimpleTree("b", listOf(c))   // b → c
        val root = SimpleTree("root", listOf(a, b))

        val edges = root.topologicalSort()
        assertEquals(4, edges.size)

        val cEdge = edges.first { it.child == "c" }
        // convert to Set for order-independent comparison
        assertEquals(
            setOf(NodeParent("a", 0), NodeParent("b", 0)),
            cEdge.parents.toSet()
        )

        verifyTopologicalCorrectness(edges)
    }

    @Test
    fun `larger dag with multiple shared nodes`() {
        /*
         *   root
         *   ├─ a ─┐
         *   │      ├─ d ─ e
         *   └─ b ─┘
         *   c ─┘
         */
        val e = SimpleTree("e")
        val d = SimpleTree("d", listOf(e))
        val c = SimpleTree("c", listOf(d))
        val b = SimpleTree("b", listOf(d))
        val a = SimpleTree("a", listOf(d))
        val root = SimpleTree("root", listOf(a, b, c))

        val edges = root.topologicalSort()

        // all 6 distinct nodes must appear once
        assertEquals(6, edges.size)

        // e must list a, b, c as parents (via d)
        val eEdge = edges.first { it.child == "e" }
        assertEquals(
            setOf(NodeParent("d", 0)),
            eEdge.parents.toSet()
        )

        verifyTopologicalCorrectness(edges)
    }
}
