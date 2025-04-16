package io.github.natanfudge.fu.util


/**
 * Allows listening to changes of an object via the [observe] method.
 * Usually, another object owns an [OwnedObservable] implementation of this interface, and emits values to it, which are received by the callback passed to [observe].
 * @sample io.github.natanfudge.fu.test.examples.util.ObservableExamples.observableExample
 */
interface Observable<T> {
    /**
     * Registers the given [onEvent] callback to be invoked when an event is emitted by the underlying source (usually an [OwnedObservable]).
     * @return a [Listener] instance which can be used to stop receiving events via [Listener.detach] when they are no longer needed, preventing memory leaks
     * and unnecessary processing.
     * @see Observable
     */
    fun observe(onEvent: (T) -> Unit): Listener<T>
}


/**
 * Represents an active observation on an [Observable]. Holds the [callback] to be executed and provides a [detach] method
 * to stop listening. This is typically returned by [Observable.observe].
 * @see Observable
 */
class Listener<T>(internal val callback: (T) -> Unit, private val observable: OwnedObservable<T>) {
    /**
     * Removes this listener from the [Observable] it was attached to, ensuring the [callback] will no longer be invoked
     * for future events. It's important to call this when the listener is no longer needed.
     * @see Observable
     */
    fun detach() {
        observable.detach(this)
    }
}


/**
 * An implementation of [Observable] held by the owner of the [Observable], allowing it [emit] values to registered listeners.
 * This is the standard way to create and manage an observable data source.
 * @see Observable
 */
class OwnedObservable<T> : Observable<T> {
    private val listeners = mutableListOf<Listener<T>>()

    /** @see Observable */
    override fun observe(onEvent: (T) -> Unit): Listener<T> {
        val listener = Listener(onEvent, this)
        listeners.add(listener)
        return listener
    }

    /**
     * Sends the given [value] to all currently registered listeners via their respective callbacks.
     * @see Observable
     */
    fun emit(value: T) {
        // Iterate over a copy in case a listener detaches itself during the callback
        listeners.toList().forEach { it.callback(value) }
    }

    /**
     * Removes the specified [listener] from the internal list, preventing it from receiving future events.
     * This is typically called by [Listener.detach].
     * @see Observable
     */
    internal fun detach(listener: Listener<T>) {
        listeners.remove(listener)
    }
}


