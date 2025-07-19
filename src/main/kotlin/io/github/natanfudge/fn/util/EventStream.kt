package io.github.natanfudge.fn.util

import io.github.natanfudge.fn.error.UnallowedFunException
import java.util.function.Consumer


/**
 * Allows listening to changes of an object via the [listenUnscoped] method.
 * Usually, another object owns an [MutEventStream] implementation of this interface, and emits values to it, which are received by the callback passed to [listenUnscoped].
 * @sample io.github.natanfudge.fn.test.example.util.ObservableExamples.observableExample
 */
interface EventStream<T> {
    companion object {
        fun <T> create() = MutEventStream<T>()
    }

    @Deprecated("use scoped listen", replaceWith = ReplaceWith("listen(onEvent)"))
            /**
             * Registers the given [onEvent] callback to be invoked when an event is emitted by the underlying source (usually an [MutEventStream]).
             * @return a [Listener] instance which can be used to stop receiving events via [Listener.close] when they are no longer needed, preventing memory leaks
             * and unnecessary processing.
             * @see EventStream
             */
    fun listenUnscoped(onEvent: Consumer<T>): Listener<T>
}

/**
 * Represents an active observation on an [EventStream]. Holds the [callback] to be executed and provides a [close] method
 * to stop listening. This is typically returned by [EventStream.listenUnscoped].
 * @see EventStream
 */
interface Listener<in T> : AutoCloseable {
    /**
     * Listener that is never called
     */
    object Stub : Listener<Any?> {
        override fun close() {

        }
    }
}

class ComposedListener<T>(private val first: Listener<T>, private val second: Listener<T>) : Listener<T> {
    override fun close() {
        first.close()
        second.close()
    }
}

fun Listener<*>.compose(other: Listener<*>): Listener<*> = ComposedListener(this, other)

@Suppress("UNCHECKED_CAST")
fun <T, R> Listener<T>.cast() = this as Listener<R>


class ListenerImpl<in T>(internal val callback: Consumer<@UnsafeVariance T>, private val observable: MutEventStream<T>) : Listener<T> {
    /**
     * Removes this listener from the [EventStream] it was attached to, ensuring the [callback] will no longer be invoked
     * for future events. It's important to call this when the listener is no longer needed.
     * @see EventStream
     */
    override fun close() {
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
    private val listeners = mutableListOf<ListenerImpl<T>>()

    private val pendingDetachments = mutableSetOf<Listener<T>>()
    private val pendingAdditions = mutableSetOf<ListenerImpl<T>>()
    private var emitting = false

    val hasListeners get() = listeners.isNotEmpty()

    fun clearListeners() {
        listeners.clear()
    }

    /** @see EventStream */
    @Deprecated("use scoped listen", replaceWith = ReplaceWith("listen(onEvent)"))
    override fun listenUnscoped(onEvent: Consumer<T>): Listener<T> {
        val listener = ListenerImpl(onEvent, this)
        if (emitting) {
            pendingAdditions.add(listener)
        } else {
            listeners.add(listener)
        }
        return listener
    }

    /**
     * Sends the given [value] to all currently registered listeners via their respective callbacks.
     * @see EventStream
     */
    fun emit(value: T) {
        emitting = true
        for (listener in listeners) {
            listener.callback.accept(value)
        }
        emitting = false

        pendingDetachments.forEach {
            detach(it)
        }
        pendingDetachments.clear()

        pendingAdditions.forEach {
            listeners.add(it)
        }
        pendingAdditions.clear()
    }

    /**
     * Removes the specified [listener] from the internal list, preventing it from receiving future events.
     * This is typically called by [Listener.close].
     * @see EventStream
     */
    internal fun detach(listener: Listener<T>) {
        // Allow detach() to work without a CME during an emit, by waiting until the emit is done and only then detaching.
        if (emitting) {
            pendingDetachments.add(listener)
            return
        }
        if (!listeners.remove(listener)) {
            if (listener is Listener.Stub) {
                throw UnallowedFunException("There's no point detaching a Listener.Stub from an EventStream.")
            }
            println("Warn: Detaching from MutEventStream failed as the listener with callback '${(listener as ListenerImpl).callback}' was probably already detached")
        }
    }
}



