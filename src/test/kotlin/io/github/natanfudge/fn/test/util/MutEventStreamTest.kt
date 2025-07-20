package io.github.natanfudge.fn.test.util

import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for MutEventStream functionality.
 * Tests cover basic operations, concurrent modification handling, multiple listeners, and edge cases.
 */
class MutEventStreamTest {

    @Test
    fun `multiple listeners receive all emitted values`() {
        val eventStream = EventEmitter<String>()
        val receivedValues1 = mutableListOf<String>()
        val receivedValues2 = mutableListOf<String>()
        val receivedValues3 = mutableListOf<String>()

        val listener1 = eventStream.listenUnscoped { receivedValues1.add(it) }
        val listener2 = eventStream.listenUnscoped { receivedValues2.add(it) }
        val listener3 = eventStream.listenUnscoped { receivedValues3.add(it) }

        eventStream.emit("first")
        eventStream.emit("second")

        assertEquals(listOf("first", "second"), receivedValues1, "First listener should receive all values")
        assertEquals(listOf("first", "second"), receivedValues2, "Second listener should receive all values")
        assertEquals(listOf("first", "second"), receivedValues3, "Third listener should receive all values")

        listener1.close()
        listener2.close()
        listener3.close()
    }

    @Test
    fun `hasListeners property works correctly`() {
        val eventStream = EventEmitter<String>()
        
        assertFalse(eventStream.hasListeners, "Should have no listeners initially")

        val listener1 = eventStream.listenUnscoped { }
        assertTrue(eventStream.hasListeners, "Should have listeners after adding one")

        val listener2 = eventStream.listenUnscoped { }
        assertTrue(eventStream.hasListeners, "Should still have listeners with multiple listeners")

        listener1.close()
        assertTrue(eventStream.hasListeners, "Should still have listeners after removing one")

        listener2.close()
        assertFalse(eventStream.hasListeners, "Should have no listeners after removing all")
    }

    @Test
    fun `clearListeners removes all listeners`() {
        val eventStream = EventEmitter<String>()
        val receivedValues1 = mutableListOf<String>()
        val receivedValues2 = mutableListOf<String>()

        eventStream.listenUnscoped { receivedValues1.add(it) }
        eventStream.listenUnscoped { receivedValues2.add(it) }

        assertTrue(eventStream.hasListeners, "Should have listeners before clearing")

        eventStream.clearListeners()

        assertFalse(eventStream.hasListeners, "Should have no listeners after clearing")

        eventStream.emit("test")

        assertTrue(receivedValues1.isEmpty(), "First listener should not receive values after clearing")
        assertTrue(receivedValues2.isEmpty(), "Second listener should not receive values after clearing")
    }

    @Test
    fun `detaching listener during emission is handled correctly`() {
        val eventStream = EventEmitter<String>()
        val receivedValues = mutableListOf<String>()
        var listener: Listener<String>? = null

        // Create a listener that detaches itself during the first emission
        listener = eventStream.listenUnscoped { value ->
            receivedValues.add(value)
            if (value == "first") {
                listener?.close() // Detach during emission
            }
        }

        // Add another listener to verify it still works
        val receivedValues2 = mutableListOf<String>()
        val listener2 = eventStream.listenUnscoped { receivedValues2.add(it) }

        eventStream.emit("first")
        eventStream.emit("second")

        assertEquals(listOf("first"), receivedValues, "Self-detaching listener should only receive first value")
        assertEquals(listOf("first", "second"), receivedValues2, "Other listener should receive all values")

        listener2.close()
    }

    @Test
    fun `adding listener during emission is handled correctly`() {
        val eventStream = EventEmitter<String>()
        val receivedValues1 = mutableListOf<String>()
        val receivedValues2 = mutableListOf<String>()

        // Create a listener that adds another listener during emission
        val listener1 = eventStream.listenUnscoped { value ->
            receivedValues1.add(value)
            if (value == "first") {
                // Add a new listener during emission
                eventStream.listenUnscoped { receivedValues2.add(it) }
            }
        }

        eventStream.emit("first")
        eventStream.emit("second")

        assertEquals(listOf("first", "second"), receivedValues1, "Original listener should receive all values")
        assertEquals(listOf("second"), receivedValues2, "New listener should only receive values after being added")

        listener1.close()
    }

    @Test
    fun testRemovingAndAddingInTheSameEmission() {
        val stream = EventEmitter<String>()
        val receivedValues = mutableListOf<String>()
        stream.listenUnscoped {
            stream.listenUnscoped {  }.close()
        }
        stream.listenUnscoped {
            receivedValues.add(it)
        }

        stream.emit("first")
        stream.emit("second")

        assertEquals(listOf("first", "second"), receivedValues)
    }

    @Test
    fun `detaching Listener Stub throws exception`() {
        val eventStream = EventEmitter<String>()
        
        val exception = assertThrows<UnallowedFunException> {
            eventStream.detach(Listener.Stub)
        }
        
        assertTrue(
            exception.message?.contains("There's no point detaching a Listener.Stub") == true,
            "Exception should mention Listener.Stub"
        )
    }

    @Test
    fun `detaching non-existent listener prints warning`() {
        val eventStream = EventEmitter<String>()
        val listener = eventStream.listenUnscoped { }
        
        // Detach the listener twice - second detach should print warning
        listener.close()
        listener.close() // This should print a warning but not throw
        
        // Test passes if no exception is thrown
    }

    @Test
    fun `ComposedListener closes both listeners`() {
        val eventStream1 = EventEmitter<String>()
        val eventStream2 = EventEmitter<Int>()
        
        val receivedStrings = mutableListOf<String>()
        val receivedInts = mutableListOf<Int>()
        
        val listener1 = eventStream1.listenUnscoped { receivedStrings.add(it) }
        val listener2 = eventStream2.listenUnscoped { receivedInts.add(it) }
        
        val composedListener = ComposedListener(listener1, listener2)
        
        eventStream1.emit("test")
        eventStream2.emit(42)
        
        assertEquals(listOf("test"), receivedStrings, "First listener should receive value")
        assertEquals(listOf(42), receivedInts, "Second listener should receive value")
        
        composedListener.close()
        
        eventStream1.emit("after close")
        eventStream2.emit(99)
        
        assertEquals(listOf("test"), receivedStrings, "First listener should not receive value after close")
        assertEquals(listOf(42), receivedInts, "Second listener should not receive value after close")
    }

    @Test
    fun `compose extension function works correctly`() {
        val eventStream1 = EventEmitter<String>()
        val eventStream2 = EventEmitter<Int>()
        
        val receivedStrings = mutableListOf<String>()
        val receivedInts = mutableListOf<Int>()
        
        val listener1 = eventStream1.listenUnscoped { receivedStrings.add(it) }
        val listener2 = eventStream2.listenUnscoped { receivedInts.add(it) }
        
        val composedListener = listener1.compose(listener2)
        
        eventStream1.emit("test")
        eventStream2.emit(42)
        
        assertEquals(listOf("test"), receivedStrings, "First listener should receive value")
        assertEquals(listOf(42), receivedInts, "Second listener should receive value")
        
        composedListener.close()
        
        eventStream1.emit("after close")
        eventStream2.emit(99)
        
        assertEquals(listOf("test"), receivedStrings, "First listener should not receive value after close")
        assertEquals(listOf(42), receivedInts, "Second listener should not receive value after close")
    }

    @Test
    fun `listener cast function works correctly`() {
        val eventStream = EventEmitter<String>()
        val receivedValues = mutableListOf<Any>()
        
        val listener = eventStream.listenUnscoped { receivedValues.add(it) }
        val castListener: Listener<Any> = listener.cast()
        
        eventStream.emit("test")
        
        assertEquals(listOf<Any>("test"), receivedValues, "Cast listener should work correctly")
        
        castListener.close()
        
        eventStream.emit("after close")
        
        assertEquals(listOf<Any>("test"), receivedValues, "Cast listener should not receive values after close")
    }

    @Test
    fun `EventStream create factory method works`() {
        val eventStream = EventStream.create<String>()
        val receivedValues = mutableListOf<String>()
        
        val listener = eventStream.listenUnscoped { receivedValues.add(it) }
        
        eventStream.emit("factory test")
        
        assertEquals(listOf("factory test"), receivedValues, "Factory-created EventStream should work")
        
        listener.close()
    }

    @Test
    fun `multiple concurrent modifications during emission`() {
        val eventStream = EventEmitter<String>()
        val receivedValues1 = mutableListOf<String>()
        val receivedValues2 = mutableListOf<String>()
        val receivedValues3 = mutableListOf<String>()
        
        var listener1: Listener<String>? = null
        var listener3: Listener<String>? = null
        
        // Listener that detaches itself and adds a new listener during emission
        listener1 = eventStream.listenUnscoped { value ->
            receivedValues1.add(value)
            if (value == "trigger") {
                listener1?.close() // Detach self
                listener3 = eventStream.listenUnscoped { receivedValues3.add(it) } // Add new listener
            }
        }
        
        val listener2 = eventStream.listenUnscoped { receivedValues2.add(it) }
        
        eventStream.emit("trigger")
        eventStream.emit("after")
        
        assertEquals(listOf("trigger"), receivedValues1, "Self-detaching listener should only receive trigger")
        assertEquals(listOf("trigger", "after"), receivedValues2, "Persistent listener should receive all values")
        assertEquals(listOf("after"), receivedValues3, "New listener should only receive values after being added")
        
        listener2.close()
        listener3?.close()
    }

    @Test
    fun `Listener Stub close does nothing`() {
        // Test that Listener.Stub.close() doesn't throw or cause issues
        Listener.Stub.close()
        // Test passes if no exception is thrown
    }
}