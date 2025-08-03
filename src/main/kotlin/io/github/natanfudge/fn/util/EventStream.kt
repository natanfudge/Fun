package io.github.natanfudge.fn.util

import io.github.natanfudge.fn.core.Resource
import io.github.natanfudge.fn.error.UnallowedFunException
import java.util.function.Consumer


/**
 * Allows listening to changes of an object via the [listenUnscoped] method.
 * Usually, another object owns an [EventEmitter] implementation of this interface, and emits values to it, which are received by the callback passed to [listenUnscoped].
 * @sample io.github.natanfudge.fn.test.example.util.ObservableExamples.observableExample
 */
interface EventStream<T> {
    companion object {
        // TODO: by event() should be used instead of this constructor inside a Fun.
        // Label should not be optional
        fun <T> create(label: String = "Unnamed Event Stream (Bug!)") = EventEmitter<T>(label)
    }

    /**
     * Registers the given [onEvent] callback to be invoked when an event is emitted by the underlying source (usually an [EventEmitter]).
     *
     * This may be called while inside yet another [listenUnscoped], and events will begin to be emitted AFTER the current event:
     * ```
     *   stream.listenUnscoped {
     *     // Works fine, will not be called until the next event
     *     if (it == 42) stream.listenUnscoped {...}
     *   }
     * ```
     * @return a [Listener] instance which can be used to stop receiving events via [Listener.close] when they are no longer needed, preventing memory leaks
     * and unnecessary processing.
     * @see EventStream
     */
    fun listenUnscoped(
        label: String = "Unnamed Listener",
        onEvent: (T) -> Unit,
    ): Listener<T>

    context(resource: Resource)
    fun listen(callback: (T) -> Unit) {
        val listener = listenUnscoped(resource.id, callback)
        resource.alsoClose(listener)
    }

}

class FilteredEventStream<T, F>(val origStream: EventStream<T>, val filter: (T) -> Boolean) : EventStream<F> {
    override fun listenUnscoped(label: String, onEvent: (F) -> Unit): Listener<F> {
        return origStream.listenUnscoped(label) {
            if (filter(it)) {
                onEvent(it as F)
            }
        } as Listener<F>
    }
}

inline fun <reified F> EventStream<out Any?>.filterIsInstance() = FilteredEventStream<Any?, F>(this as EventStream<Any?>) { it is F }
fun <T> EventStream<T>.filter(condition: (T) -> Boolean) = FilteredEventStream<T, T>(this, condition)

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


class ListenerImpl<in T>(internal val callback: Consumer<@UnsafeVariance T>, private val observable: EventEmitter<T>, val label: String) : Listener<T> {


    /**
     * Removes this listener from the [EventStream] it was attached to, ensuring the [callback] will no longer be invoked
     * for future events. It's important to call this when the listener is no longer needed.
     * @see EventStream
     */
    override fun close() {
        observable.detach(this)
    }

    override fun toString(): String {
        return "${observable.label}->$label"
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

// TODO: by event() should be used instead of this constructor
//         // Label should not be optional
class EventEmitter<T> internal constructor(val label: String = "Unnamed Event Emitter (Bug!)") : EventStream<T> {
    private val listeners = mutableListOf<ListenerImpl<T>>()

    val hasListeners get() = listeners.isNotEmpty()

    /**
     * Kept at an instance level in the object so that when we remove an object before the active iteration index,
     * we can reduce the activeIterationIndex by 1 and avoid skipping an object in emit()
     */
    private var activeIterationIndex = 0

    /**
     * Kept at an instance level in the object so that when we remove an an object, we can reduce iterationEndIndex by 1 to not go out of bounds.
     * Note that we don't want to keep checking listeners.size because then it would process newer elements that were added with [listenUnscoped]
     * during the same emittion, which we don't want.
     */
    private var iterationEndIndex = -1

    fun clearListeners() {
        listeners.clear()
    }

    /**
     * This may be called while inside yet another [listenUnscoped], and events will begin to be emitted AFTER the current event:
     * ```
     * stream.listenUnscoped {
     *      if (it == 42) stream.listenUnscoped {...} // Works fine, will not be called until the next event
     * }
     * ```
     *
     * */
    override fun listenUnscoped(label: String, onEvent: (T) -> Unit): Listener<T> {
        val listener = ListenerImpl(onEvent, this, label)
        listeners.add(listener)
        return listener
    }

    inline fun <reified F : T> listenUnscoped2(label: String, crossinline callback: (F) -> Unit): Listener<T> = listenUnscoped(label) {
        if (it is F) {
            callback(it)
        }
    }

    /**
     * Sends the given [value] to all currently registered listeners via their respective callbacks.
     * @see EventStream
     */
    fun emit(value: T) {
        activeIterationIndex = 0
        iterationEndIndex = listeners.size
        while (activeIterationIndex < iterationEndIndex) {
            val listener = listeners[activeIterationIndex]
            activeIterationIndex++
            listener.callback.accept(value)
        }
    }

    operator fun invoke(value: T) = emit(value)

    /**
     * Removes the specified [listener] from the internal list, preventing it from receiving future events.
     * This is typically called by [Listener.close].
     * @see EventStream
     */
    internal fun detach(listener: Listener<T>) {
        val removeIndex = listeners.indexOf(listener)
        if (removeIndex != -1) {
            listeners.removeAt(removeIndex)
            // Make sure to not reduce the iteration end index for listeners that are not part of the active iteration zone anyway (they were recently added)
            if (removeIndex < iterationEndIndex) {
                // No need to process this anymore
                iterationEndIndex--
            }
            if (removeIndex < activeIterationIndex) {
                //    activeIterationIndex
                //          |
                //          |
                // [][][][X][][][][][]
                //        |
                //        |
                //     removeIndex
                //
                // If we remove up until that point, the array will shift to the left, and the element at activeIterationIndex will be skipped.
                // So we move activeIterationIndex one place back to account for the shift.
                activeIterationIndex--
            } else {
                //      activeIterationIndex
                //          |
                //          |
                // [][][][][X][][][][]
                //          |
                //          |
                //        removeIndex
                //
                // In this case, the deleted value will be skipped, which is fine.
                // If removeIndex > activeIterationIndex:
                //      activeIterationIndex
                //          |
                //          |
                // [][][][][][X][][][]
                //            |
                //            |
                //        removeIndex
                // Only later elements will be affected, and the iteration will work correctly.
            }
        } else {
            if (listener is Listener.Stub) {
                throw UnallowedFunException("There's no point detaching a Listener.Stub from an EventStream.")
            }
            println("Warn: Detaching $listener failed as the listener was probably already detached. Existing attachments: $listeners")
        }
    }
}


