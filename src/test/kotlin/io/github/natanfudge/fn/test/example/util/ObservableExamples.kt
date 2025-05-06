package io.github.natanfudge.fn.test.example.util

import io.github.natanfudge.fn.util.MutEventStream
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ObservableExamples {
    @Test
    fun observableExample() {
        // Create an observable that emits String values
        val observable = MutEventStream<String>()

        // Track emitted values
        val receivedValues = mutableListOf<String>()

        // Observe the observable and add received values to our list
        val listener = observable.listen { value ->
            receivedValues.add(value)
        }

        // Emit some values
        observable.emit("Hello")
        observable.emit("World")

        // Verify the values were received
        assertEquals(listOf("Hello", "World"), receivedValues, "receivedValues")

        // Detach the listener
        listener.close()

        // Emit another value that should not be received
        observable.emit("Not received")

        // Verify the detached listener doesn't receive new values
        assertEquals(listOf("Hello", "World"), receivedValues, "receivedValues")
    }
}