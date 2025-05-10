package io.github.natanfudge.fn.test.util

import io.github.natanfudge.fn.error.UnfunStateException
import io.github.natanfudge.fn.util.Lifecycle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach

class NewLifecyclesTest {

    private lateinit var rootTracker: CallTracker<String, String>
    private lateinit var childTracker: CallTracker<String, String>
    private lateinit var childTracker2: CallTracker<String, String>
    private lateinit var multiParentChildTracker: CallTracker<List<Any>, String> // P is List<Any> for bind2 internal
    private lateinit var autoCloseTracker: AutoCloseableTracker

    // Helper class for tracking calls
    class CallTracker<P_Track, T_Track>(val id: String = "") {
        val receivedP = mutableListOf<P_Track>()
        val producedT = mutableListOf<T_Track>() // Tracks what the start lambda produced
        val stoppedWithT = mutableListOf<T_Track>() // Tracks what the stop lambda received

        var startCallCount = 0
        var stopCallCount = 0

        fun startFn(transform: (P_Track) -> T_Track): (P_Track) -> T_Track {
            return { p ->
                println("Tracker '$id': START called with $p")
                receivedP.add(p)
                startCallCount++
                val result = transform(p)
                producedT.add(result)
                result
            }
        }
        
        // Specific for bind2 where P_Track is List<Any> but user provides (P1, P2) -> T
        fun <P1, P2> startFnForBind2(actualStart: (P1, P2) -> T_Track): (List<Any>) -> T_Track {
            return { parentsList ->
                println("Tracker '$id': START (bind2) called with $parentsList")
                @Suppress("UNCHECKED_CAST")
                receivedP.add(parentsList as P_Track)
                startCallCount++
                val p1 = parentsList[0] as P1
                val p2 = parentsList[1] as P2
                val result = actualStart(p1, p2)
                producedT.add(result)
                result
            }
        }

        fun stopFn(): (T_Track) -> Unit {
            return { t ->
                println("Tracker '$id': STOP called with $t")
                stoppedWithT.add(t)
                stopCallCount++
            }
        }
        
        fun clear() {
            receivedP.clear()
            producedT.clear()
            stoppedWithT.clear()
            startCallCount = 0
            stopCallCount = 0
        }
    }
    
    class AutoCloseableTracker {
        val closedItems = mutableListOf<String>()
        fun clear() {
            closedItems.clear()
        }
    }

    class MyAutoCloseable(val id: String, private val tracker: AutoCloseableTracker? = null) : AutoCloseable {
        var closed = false
        override fun close() {
            if (closed) {
                // This can happen if stop() also calls close() and then Lifecycle calls it again.
                // For testing, we'll assume stop() doesn't redundantly close.
                println("MyAutoCloseable '$id' already closed, attempted re-close.")
                return
            }
            closed = true
            tracker?.closedItems?.add(id)
            println("MyAutoCloseable '$id' closed via AutoCloseable.")
        }
         fun manualStop() {
             println("MyAutoCloseable '$id' manually stopped.")
             // if not also AutoCloseable, this would be the only cleanup
         }
    }

    @BeforeEach
    fun setUp() {
        rootTracker = CallTracker("root")
        childTracker = CallTracker("child1")
        childTracker2 = CallTracker("child2")
        multiParentChildTracker = CallTracker("multiParentChild")
        autoCloseTracker = AutoCloseableTracker()
    }

    @Test
    fun `single lifecycle - create, start, end`() {
        val lc = Lifecycle.Companion.create<String, String>(
            label = "TestLC",
            start = rootTracker.startFn { seed -> "Started with $seed" },
            stop = rootTracker.stopFn()
        )

        lc.start("initial_seed")
        assertEquals(1, rootTracker.startCallCount)
        assertEquals("initial_seed", rootTracker.receivedP.first())
        assertEquals("Started with initial_seed", rootTracker.producedT.first())
        assertEquals(0, rootTracker.stopCallCount)

        lc.end()
        assertEquals(1, rootTracker.stopCallCount)
        assertEquals("Started with initial_seed", rootTracker.stoppedWithT.first())
    }

    @Test
    fun `single lifecycle - restart`() {
        val lc = Lifecycle.Companion.create<String, String>(
            label = "RestartLC",
            start = rootTracker.startFn { seed -> "S:$seed" },
            stop = rootTracker.stopFn()
        )

        lc.start("seed1")
        assertEquals(1, rootTracker.startCallCount)
        assertEquals("S:seed1", rootTracker.producedT.last())

        lc.restart() // Restarts with existing parent state "seed1"
        assertEquals(2, rootTracker.startCallCount)
        assertEquals("seed1", rootTracker.receivedP.last()) // startFn receives original seed
        assertEquals("S:seed1", rootTracker.producedT.last())
        assertEquals(1, rootTracker.stopCallCount) // From the end() part of restart
        assertEquals("S:seed1", rootTracker.stoppedWithT.last())
    }

    @Test
    fun `single lifecycle - restart with new seed`() {
        val lc = Lifecycle.Companion.create<String, String>(
            label = "RestartNewSeedLC",
            start = rootTracker.startFn { seed -> "S:$seed" },
            stop = rootTracker.stopFn()
        )

        lc.start("seed1")
        assertEquals("S:seed1", rootTracker.producedT.last())

        lc.restart("seed2", 0) // parentIndex 0 for root
        assertEquals(2, rootTracker.startCallCount)
        assertEquals("seed2", rootTracker.receivedP.last())
        assertEquals("S:seed2", rootTracker.producedT.last())
        assertEquals(1, rootTracker.stopCallCount)
        assertEquals("S:seed1", rootTracker.stoppedWithT.last()) // Stopped with old state
    }

    @Test
    fun `parent-child lifecycle - start and end propagation`() {
        val parentLc = Lifecycle.Companion.create<String, String>(
            label = "Parent",
            start = rootTracker.startFn { "P:$it" },
            stop = rootTracker.stopFn()
        )
        val childLc = parentLc.bind<String, String>(
            label = "Child",
            start = childTracker.startFn { parentState -> "C:$parentState" },
            stop = childTracker.stopFn()
        )

        parentLc.start("parent_seed")
        assertEquals(1, rootTracker.startCallCount)
        assertEquals("P:parent_seed", rootTracker.producedT.first())
        assertEquals(1, childTracker.startCallCount)
        assertEquals("P:parent_seed", childTracker.receivedP.first()) // Child receives parent's state
        assertEquals("C:P:parent_seed", childTracker.producedT.first())

        parentLc.end()
        assertEquals(1, rootTracker.stopCallCount)
        assertEquals("P:parent_seed", rootTracker.stoppedWithT.first())
        assertEquals(1, childTracker.stopCallCount)
        assertEquals("C:P:parent_seed", childTracker.stoppedWithT.first()) // Child stops after parent
    }
    
    @Test
    fun `parent-child lifecycle - parent restart propagates to child`() {
        val parentLc = Lifecycle.Companion.create<String, String>(
            label = "ParentRestart",
            start = rootTracker.startFn { "P:$it" },
            stop = rootTracker.stopFn()
        )
        parentLc.bind<String, String>(
            label = "ChildRestart",
            start = childTracker.startFn { parentState -> "C:$parentState" },
            stop = childTracker.stopFn()
        )

        parentLc.start("initial")
        assertEquals(1, rootTracker.startCallCount)
        assertEquals(1, childTracker.startCallCount)
        assertEquals("P:initial", childTracker.receivedP.last())

        parentLc.restart("restarted_seed", 0)
        assertEquals(2, rootTracker.startCallCount) // Parent restarted
        assertEquals("restarted_seed", rootTracker.receivedP.last())
        assertEquals("P:restarted_seed", rootTracker.producedT.last())
        assertEquals(1, rootTracker.stopCallCount) // Parent old instance stopped
        assertEquals("P:initial", rootTracker.stoppedWithT.last())


        assertEquals(2, childTracker.startCallCount) // Child also restarted
        assertEquals("P:restarted_seed", childTracker.receivedP.last()) // Child gets new parent state
        assertEquals("C:P:restarted_seed", childTracker.producedT.last())
        assertEquals(1, childTracker.stopCallCount) // Child old instance stopped
        assertEquals("C:P:initial", childTracker.stoppedWithT.last())
    }


    @Test
    fun `multi-parent lifecycle (bind2) - starts only after all parents start`() {
        val parentA = Lifecycle.Companion.create<String, String>("ParentA", rootTracker.startFn { "A:$it" }, rootTracker.stopFn())
        val parentB = Lifecycle.Companion.create<String, String>("ParentB", childTracker.startFn { "B:$it" }, childTracker.stopFn())

        // Note: The P type for the returned lifecycle from bind2 is cast to the first parent's T type.
        // Internally, its LifecycleData expects List<Any>.
        val childC = parentA.bind2<String, String>(
            parentB, "ChildC",
            start = multiParentChildTracker.startFnForBind2 { pA_state: String, pB_state: String -> "C_from($pA_state, $pB_state)" },
            stop = multiParentChildTracker.stopFn()
        )

        parentA.start("dataA")
        assertEquals(1, rootTracker.startCallCount) // ParentA started
        assertEquals(0, childTracker.startCallCount) // ParentB not started
        assertEquals(0, multiParentChildTracker.startCallCount) // ChildC not started

        parentB.start("dataB")
        assertEquals(1, childTracker.startCallCount) // ParentB started
        assertEquals(1, multiParentChildTracker.startCallCount) // ChildC started
        assertEquals(listOf("A:dataA", "B:dataB"), multiParentChildTracker.receivedP.first())
        assertEquals("C_from(A:dataA, B:dataB)", multiParentChildTracker.producedT.first())

        parentA.end() // Ending one parent
        // ChildC should still be alive if its stop logic depends on all parents,
        // but Lifecycle's default is that if any parent stops, children stop if they were started by it.
        // The current implementation of end() visits bottom-up unique, so childC will be stopped.
        assertEquals(1, multiParentChildTracker.stopCallCount)
        assertEquals("C_from(A:dataA, B:dataB)", multiParentChildTracker.stoppedWithT.first())
        assertEquals(1, rootTracker.stopCallCount) // ParentA stopped

        // ParentB is still technically "active" but child was already stopped.
        parentB.end()
        assertEquals(1, childTracker.stopCallCount) // ParentB stopped
    }
    
    @Test
    fun `multi-parent lifecycle (bind2) - restart one parent updates child`() {
        val parentA = Lifecycle.Companion.create<String, String>("ParentA_re", rootTracker.startFn { "A:$it" }, rootTracker.stopFn())
        val parentB = Lifecycle.Companion.create<String, String>("ParentB_re", childTracker.startFn { "B:$it" }, childTracker.stopFn())

        val childC = parentA.bind2<String, String>(
            parentB, "ChildC_re",
            start = multiParentChildTracker.startFnForBind2 { pA_state: String, pB_state: String -> "C($pA_state, $pB_state)" },
            stop = multiParentChildTracker.stopFn()
        )

        parentA.start("a1")
        parentB.start("b1") // ChildC starts with (A:a1, B:b1)
        assertEquals(1, multiParentChildTracker.startCallCount)
        assertEquals(listOf("A:a1", "B:b1"), multiParentChildTracker.receivedP.last())
        assertEquals("C(A:a1, B:b1)", multiParentChildTracker.producedT.last())

        rootTracker.clear()
        childTracker.clear() // parentB tracker
        multiParentChildTracker.clear()

        parentA.restart("a2", 0) // Restart ParentA with new data
        
        // ParentA restarted
        assertEquals(1, rootTracker.startCallCount)
        assertEquals("a2", rootTracker.receivedP.last())
        assertEquals("A:a2", rootTracker.producedT.last())
        assertEquals(1, rootTracker.stopCallCount) // Old A:a1 stopped
        assertEquals("A:a1", rootTracker.stoppedWithT.last())

        // ParentB was not directly affected
        assertEquals(0, childTracker.startCallCount)
        assertEquals(0, childTracker.stopCallCount)
        
        // ChildC should have restarted due to ParentA's restart
        // It receives new data from A and existing data from B
        assertEquals(1, multiParentChildTracker.startCallCount)
        // The visitCounter logic and "squeeze out" should make childC re-run.
        // parentState for childC becomes [A:a2, B:b1]
        // The startRecur for childC will be called with seedValue=A:a2 and parentIndex=0
        // Then the while loop in parentA.start() should trigger childC to run fully.
        assertEquals(listOf("A:a2", "B:b1"), multiParentChildTracker.receivedP.last())
        assertEquals("C(A:a2, B:b1)", multiParentChildTracker.producedT.last())
        assertEquals(1, multiParentChildTracker.stopCallCount) // Old C(A:a1, B:b1) stopped
        assertEquals("C(A:a1, B:b1)", multiParentChildTracker.stoppedWithT.last())
    }


    @Test
    fun `lifecycle with AutoCloseable resource`() {
        val lc = Lifecycle.Companion.create<String, MyAutoCloseable>(
            label = "AutoCloseLC",
            start = { seed -> MyAutoCloseable("ac_$seed", autoCloseTracker) },
            stop = { ac_resource -> ac_resource.manualStop() } // stop might do other things
        )

        lc.start("data")
        val resource = rootTracker.producedT.firstOrNull() // Not using rootTracker here, direct check
        
        lc.end()
        assertTrue(autoCloseTracker.closedItems.contains("ac_data"), "AutoCloseable should be closed")
    }
    
    @Test
    fun `failure - start lifecycle with null seed when parentState is also null`() {
        val lc = Lifecycle.Companion.create<String?, String>( // Allow P to be nullable for test
            label = "NullSeedFailLC",
            start = { seed -> "Started: ${seed ?: "null_seed"}" },
            stop = { }
        )

        val exception = assertThrows<IllegalStateException> {
            lc.start(null) // seedValue is null, parentState is initially null
        }
        println("Caught expected exception: ${exception.message}")
        assertTrue(exception.message?.contains("startSingle should not have been run with a null seedValue when there is no preexisting parent state") == true)
    }
    
    @Test
    fun `failure - restart lifecycle that never started (null seed, null parentState)`() {
        val lc = Lifecycle.Companion.create<String, String>(
            label = "RestartNeverStartedLC",
            start = { it },
            stop = { }
        )

        val exception = assertThrows<IllegalStateException> {
            lc.restart() // Calls start(null), parentState is null
        }
        println("Caught expected exception: ${exception.message}")
        assertTrue(exception.message?.contains("startSingle should not have been run with a null seedValue when there is no preexisting parent state") == true)
    }

    @Test
    fun `failure - invalid parentIndex on start`() {
        val lc = Lifecycle.Companion.create<String, String>(
            label = "InvalidParentIndexLC",
            start = { it },
            stop = { }
        )
        // lc has parentCount = 0 internally for its root LifecycleData

        val exception = assertThrows<IllegalArgumentException> {
            lc.start("seed", parentIndex = 1) // parentCount is 0, so index 1 is invalid
        }
        println("Caught expected exception: ${exception.message}")
        assertTrue(exception.message?.contains("Lifecycle 'InvalidParentIndexLC' was initialized with a value belonging to parent index 1, but the lifecycle has no parents") == true)
    }
    
    @Test
    fun `failure - starting a multi-parent child directly via restart (leads to null seed error)`() {
        // This test demonstrates that restarting a multi-parent child that hasn't been fully initialized
        // by its actual parents will lead to the "null seedValue when there is no preexisting parent state" error,
        // because its internal parentState (which is a List) won't be properly formed.
        val parentA = Lifecycle.Companion.create<String, String>("ParentA_fail", start = { "A:$it" }, stop = {})
        val parentB = Lifecycle.Companion.create<String, String>("ParentB_fail", start = { "B:$it" }, stop = {})

        val childC = parentA.bind2<String, String>(parentB, "ChildC_fail",
            start = { pa, pb -> "C($pa, $pb)" },
            stop = {}
        )
        // At this point, childC's LifecycleData has parentCount = 2, but its parentState is null.

        val exception = assertThrows<IllegalStateException> {
            childC.restart() // Tries to start with null seed, parentState for childC is null
        }
        println("Caught expected exception for multi-parent direct restart: ${exception.message}")
        assertTrue(exception.message?.contains("startSingle should not have been run with a null seedValue when there is no preexisting parent state") == true)
    }

    @Test
    fun `ending a lifecycle that was not started should not throw error (logs warning)`() {
        val lc = Lifecycle.Companion.create<String, String>(
            label = "NotStartedLC",
            start = rootTracker.startFn { "S:$it" },
            stop = rootTracker.stopFn()
        )
        // We can't easily verify logs here, but we ensure no exception is thrown
        // and stop is not called.
        assertDoesNotThrow {
            lc.end()
        }
        assertEquals(0, rootTracker.startCallCount)
        assertEquals(0, rootTracker.stopCallCount)
    }
    
    @Test
    fun `complex hierarchy with shared child (diamond problem)`() {
        val root = Lifecycle.Companion.create<String, String>("Root", rootTracker.startFn { "R:$it" }, rootTracker.stopFn())
        
        val childA = root.bind<String, String>("ChildA", childTracker.startFn { "CA_from($it)" }, childTracker.stopFn())
        val childB = root.bind<String, String>("ChildB", childTracker2.startFn { "CB_from($it)" }, childTracker2.stopFn())

        // GrandChild depends on ChildA and ChildB
        val grandChild = childA.bind2<String, String>(childB, "GrandChild",
            start = multiParentChildTracker.startFnForBind2 { cA_state: String, cB_state: String -> "GC_from($cA_state, $cB_state)" },
            stop = multiParentChildTracker.stopFn()
        )

        root.start("root_data")

        // Root starts
        assertEquals(1, rootTracker.startCallCount)
        assertEquals("R:root_data", rootTracker.producedT.first())

        // ChildA and ChildB start (order might vary depending on internal list iteration)
        assertEquals(1, childTracker.startCallCount) // ChildA
        assertTrue(childTracker.receivedP.contains("R:root_data"))
        assertTrue(childTracker.producedT.contains("CA_from(R:root_data)"))

        assertEquals(1, childTracker2.startCallCount) // ChildB
        assertTrue(childTracker2.receivedP.contains("R:root_data"))
        assertTrue(childTracker2.producedT.contains("CB_from(R:root_data)"))
        
        // GrandChild starts
        assertEquals(1, multiParentChildTracker.startCallCount)
        // The order in the list for GrandChild's parents depends on bind order
        // childA.bind2(childB,...) -> childA is parent 0, childB is parent 1
        assertEquals(listOf("CA_from(R:root_data)", "CB_from(R:root_data)"), multiParentChildTracker.receivedP.first())
        assertEquals("GC_from(CA_from(R:root_data), CB_from(R:root_data))", multiParentChildTracker.producedT.first())

        rootTracker.clear(); childTracker.clear(); childTracker2.clear(); multiParentChildTracker.clear()
        
        root.end()
        // All should be stopped, GrandChild first, then ChildA/B, then Root
        assertEquals(1, multiParentChildTracker.stopCallCount) // GrandChild stopped
        assertEquals("GC_from(CA_from(R:root_data), CB_from(R:root_data))", multiParentChildTracker.stoppedWithT.first())
        
        assertEquals(1, childTracker.stopCallCount) // ChildA stopped
        assertEquals("CA_from(R:root_data)", childTracker.stoppedWithT.first())
        
        assertEquals(1, childTracker2.stopCallCount) // ChildB stopped
        assertEquals("CB_from(R:root_data)", childTracker2.stoppedWithT.first())
        
        assertEquals(1, rootTracker.stopCallCount) // Root stopped
        assertEquals("R:root_data", rootTracker.stoppedWithT.first())
    }
}