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
        val eventStream = EventStream.create<String>("test-stream")
        val receivedValues1 = mutableListOf<String>()
        val receivedValues2 = mutableListOf<String>()
        val receivedValues3 = mutableListOf<String>()

        val listener1 = eventStream.listenUnscoped("listener1") { receivedValues1.add(it) }
        val listener2 = eventStream.listenUnscoped("listener2") { receivedValues2.add(it) }
        val listener3 = eventStream.listenUnscoped("listener3") { receivedValues3.add(it) }

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
        val eventStream = EventStream.create<String>("hasListeners-test")
        
        assertFalse(eventStream.hasListeners, "Should have no listeners initially")

        val listener1 = eventStream.listenUnscoped("listener1") { }
        assertTrue(eventStream.hasListeners, "Should have listeners after adding one")

        val listener2 = eventStream.listenUnscoped("listener2") { }
        assertTrue(eventStream.hasListeners, "Should still have listeners with multiple listeners")

        listener1.close()
        assertTrue(eventStream.hasListeners, "Should still have listeners after removing one")

        listener2.close()
        assertFalse(eventStream.hasListeners, "Should have no listeners after removing all")
    }

    @Test
    fun `clearListeners removes all listeners`() {
        val eventStream = EventStream.create<String>("clearListeners-test")
        val receivedValues1 = mutableListOf<String>()
        val receivedValues2 = mutableListOf<String>()

        eventStream.listenUnscoped("listener1") { receivedValues1.add(it) }
        eventStream.listenUnscoped("listener2") { receivedValues2.add(it) }

        assertTrue(eventStream.hasListeners, "Should have listeners before clearing")

        eventStream.clearListeners()

        assertFalse(eventStream.hasListeners, "Should have no listeners after clearing")

        eventStream.emit("test")

        assertTrue(receivedValues1.isEmpty(), "First listener should not receive values after clearing")
        assertTrue(receivedValues2.isEmpty(), "Second listener should not receive values after clearing")
    }

    @Test
    fun `detaching listener during emission is handled correctly`() {
        val eventStream = EventStream.create<String>("detaching-test")
        val receivedValues = mutableListOf<String>()
        var listener: Listener<String>? = null

        // Create a listener that detaches itself during the first emission
        listener = eventStream.listenUnscoped("self-detaching") { value ->
            receivedValues.add(value)
            if (value == "first") {
                listener?.close() // Detach during emission
            }
        }

        // Add another listener to verify it still works
        val receivedValues2 = mutableListOf<String>()
        val listener2 = eventStream.listenUnscoped("persistent") { receivedValues2.add(it) }

        eventStream.emit("first")
        eventStream.emit("second")

        assertEquals(listOf("first"), receivedValues, "Self-detaching listener should only receive first value")
        assertEquals(listOf("first", "second"), receivedValues2, "Other listener should receive all values")

        listener2.close()
    }

    @Test
    fun `adding listener during emission is handled correctly`() {
        val eventStream = EventStream.create<String>("adding-test")
        val receivedValues1 = mutableListOf<String>()
        val receivedValues2 = mutableListOf<String>()

        // Create a listener that adds another listener during emission
        val listener1 = eventStream.listenUnscoped("original") { value ->
            receivedValues1.add(value)
            if (value == "first") {
                // Add a new listener during emission
                eventStream.listenUnscoped("added-during-emission") { receivedValues2.add(it) }
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
        val stream = EventStream.create<String>("removing-adding-test")
        val receivedValues = mutableListOf<String>()
        stream.listenUnscoped("remover") {
            stream.listenUnscoped("temp") {  }.close()
        }
        stream.listenUnscoped("receiver") {
            receivedValues.add(it)
        }

        stream.emit("first")
        stream.emit("second")

        assertEquals(listOf("first", "second"), receivedValues)
    }

    @Test
    fun `detaching Listener Stub throws exception`() {
        val eventStream = EventStream.create<String>("stub-test")
        
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
        val eventStream = EventStream.create<String>("warning-test")
        val listener = eventStream.listenUnscoped("test-listener") { }
        
        // Detach the listener twice - second detach should print warning
        listener.close()
        listener.close() // This should print a warning but not throw
        
        // Test passes if no exception is thrown
    }

    @Test
    fun `ComposedListener closes both listeners`() {
        val eventStream1 = EventStream.create<String>("composed-test1")
        val eventStream2 = EventStream.create<Int>("composed-test2")
        
        val receivedStrings = mutableListOf<String>()
        val receivedInts = mutableListOf<Int>()
        
        val listener1 = eventStream1.listenUnscoped("string-listener") { receivedStrings.add(it) }
        val listener2 = eventStream2.listenUnscoped("int-listener") { receivedInts.add(it) }
        
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
        val eventStream1 = EventStream.create<String>("compose-test1")
        val eventStream2 = EventStream.create<Int>("compose-test2")
        
        val receivedStrings = mutableListOf<String>()
        val receivedInts = mutableListOf<Int>()
        
        val listener1 = eventStream1.listenUnscoped("string-listener") { receivedStrings.add(it) }
        val listener2 = eventStream2.listenUnscoped("int-listener") { receivedInts.add(it) }
        
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
        val eventStream = EventStream.create<String>("cast-test")
        val receivedValues = mutableListOf<Any>()
        
        val listener = eventStream.listenUnscoped("cast-listener") { receivedValues.add(it) }
        val castListener: Listener<Any> = listener.cast()
        
        eventStream.emit("test")
        
        assertEquals(listOf<Any>("test"), receivedValues, "Cast listener should work correctly")
        
        castListener.close()
        
        eventStream.emit("after close")
        
        assertEquals(listOf<Any>("test"), receivedValues, "Cast listener should not receive values after close")
    }

    @Test
    fun `EventStream create factory method works`() {
        val eventStream = EventStream.create<String>("factory-test")
        val receivedValues = mutableListOf<String>()
        
        val listener = eventStream.listenUnscoped("factory-listener") { receivedValues.add(it) }
        
        eventStream.emit("factory test")
        
        assertEquals(listOf("factory test"), receivedValues, "Factory-created EventStream should work")
        
        listener.close()
    }

    @Test
    fun `multiple concurrent modifications during emission`() {
        val eventStream = EventStream.create<String>("concurrent-test")
        val receivedValues1 = mutableListOf<String>()
        val receivedValues2 = mutableListOf<String>()
        val receivedValues3 = mutableListOf<String>()
        
        var listener1: Listener<String>? = null
        var listener3: Listener<String>? = null
        
        // Listener that detaches itself and adds a new listener during emission
        listener1 = eventStream.listenUnscoped("self-detaching-adder") { value ->
            receivedValues1.add(value)
            if (value == "trigger") {
                listener1?.close() // Detach self
                listener3 = eventStream.listenUnscoped("added-during-trigger") { receivedValues3.add(it) } // Add new listener
            }
        }
        
        val listener2 = eventStream.listenUnscoped("persistent") { receivedValues2.add(it) }
        
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