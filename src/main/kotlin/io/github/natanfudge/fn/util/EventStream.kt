package io.github.natanfudge.fn.util


/**
 * Allows listening to changes of an object via the [listen] method.
 * Usually, another object owns an [MutEventStream] implementation of this interface, and emits values to it, which are received by the callback passed to [listen].
 * @sample io.github.natanfudge.fn.test.example.util.ObservableExamples.observableExample
 */
interface EventStream<T> {
    /**
     * Registers the given [onEvent] callback to be invoked when an event is emitted by the underlying source (usually an [MutEventStream]).
     * @return a [Listener] instance which can be used to stop receiving events via [Listener.detach] when they are no longer needed, preventing memory leaks
     * and unnecessary processing.
     * @see EventStream
     */
    fun listen(onEvent: (T) -> Unit): Listener<T>
}


/**
 * Represents an active observation on an [EventStream]. Holds the [callback] to be executed and provides a [detach] method
 * to stop listening. This is typically returned by [EventStream.listen].
 * @see EventStream
 */
class Listener<in T>(internal val callback: (T) -> Unit, private val observable: MutEventStream<T>) {
    /**
     * Removes this listener from the [EventStream] it was attached to, ensuring the [callback] will no longer be invoked
     * for future events. It's important to call this when the listener is no longer needed.
     * @see EventStream
     */
    fun detach() {
        observable.detach(this)
    }
}


 // IDEA: it would make sense for eventstreams to have a string identifier to be able to track them in runtime.
// Then inside components we can have an API like this
// class MyThing(override val id: String): Component {
//      val firedFireballs by event<Int>()
//      And then firedFireballs has an invoke() method that can be used as firedFireballs()
//       But more importantly, the name of firedFireballs is saved as a string for runtime inspection,
//  AND the id is captured for runtime inspection.
// }
//
//

/**
 * An implementation of [EventStream] held by the owner of the [EventStream], allowing it [emit] values to registered listeners.
 * This is the standard way to create and manage an observable data source.
 * @see EventStream
 */
class MutEventStream<T> : EventStream<T> {
    private val listeners = mutableListOf<Listener<T>>()

    /** @see EventStream */
    override fun listen(onEvent: (T) -> Unit): Listener<T> {
        val listener = Listener(onEvent, this)
        listeners.add(listener)
        return listener
    }

    /**
     * Sends the given [value] to all currently registered listeners via their respective callbacks.
     * @see EventStream
     */
    fun emit(value: T) {
        // Iterate over a copy in case a listener detaches itself during the callback
        listeners.toList().forEach { it.callback(value) }
    }

    /**
     * Removes the specified [listener] from the internal list, preventing it from receiving future events.
     * This is typically called by [Listener.detach].
     * @see EventStream
     */
    internal fun detach(listener: Listener<T>) {
        listeners.remove(listener)
    }
}


