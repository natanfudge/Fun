package io.github.natanfudge.fn.compose.utils

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Runs code whenever a MutableState changes.
 * Note that this value should be used INSTEAD of the original [MutableState], as it does not mutate to call the listener on change.
 * [callback] is called AFTER the state is mutated.
 */
fun <T> MutableState<T>.listen(callback: (T) -> Unit): MutableState<T> = ListenedState(this, callback)

/**
 * Maps a state from the type [I] to the type [O]
 */
fun <I, O> State<I>.map(mapper: (I) -> O): State<O> = MappedState(this, mapper)

/**
 * Maps a state from the type [I] to the type [O]
 */
fun <I, O> MutableState<I>.map(mapper: (I) -> O): State<O> = MappedState(this, mapper)

fun <T> mutableState(value: T, onChanged: (T) -> Unit) = SimpleMutableState(value, onChanged)

/**
 * Runs code whenever a MutableState changes.
 * Note that this value should be used INSTEAD of the original [MutableState], as it does not mutate to call the listener on change.
 * [callback] is called AFTER the state is mutated.
 */
class ListenedState<T>(private val orig: MutableState<T>, private val callback: (T) -> Unit) : MutableState<T> {
    override var value: T = orig.value
        get() {
            return orig.value // Important: reference orig.value on every referencing of this value
        }
        set(value) {
            if (field != value) {
                field = value
                orig.value = value
                callback(value)
            }
        }

    override fun component1(): T {
        return value
    }

    override fun component2(): (T) -> Unit = {
        value = it
    }
}

class ListenedList<T>(
    private val orig: MutableList<T>,
    private val onChange: (List<T>) -> Unit
) : MutableList<T> by orig {

    /* ---------- helpers ---------- */

    private inline fun <R> change(block: () -> R): R {
        val result = block()
        onChange(orig)
        return result
    }

    /* ---------- direct mutations ---------- */

    override fun add(element: T): Boolean = change { orig.add(element) }

    override fun add(index: Int, element: T) = change { orig.add(index, element) }

    override fun addAll(elements: Collection<T>): Boolean = if (elements.isEmpty()) false else change { orig.addAll(elements) }

    override fun addAll(index: Int, elements: Collection<T>): Boolean = if (elements.isEmpty()) false else change { orig.addAll(index, elements) }

    override fun remove(element: T): Boolean = if (!orig.contains(element)) false else change { orig.remove(element) }

    override fun removeAt(index: Int): T = change { orig.removeAt(index) }

    override fun removeAll(elements: Collection<T>): Boolean = if (elements.isEmpty()) false else change { orig.removeAll(elements) }

    override fun retainAll(elements: Collection<T>): Boolean = change { orig.retainAll(elements) }

    override fun clear() = if (orig.isNotEmpty()) change { orig.clear() } else Unit

    override fun set(index: Int, element: T): T = change { orig.set(index, element) }

    /* ---------- iterator wrappers ---------- */

    override fun iterator(): MutableIterator<T> =
        object : MutableIterator<T> {
            private val it = orig.iterator()
            override fun hasNext() = it.hasNext()
            override fun next() = it.next()
            override fun remove() = change { it.remove() }
        }

    override fun listIterator(): MutableListIterator<T> =
        listIterator(0)

    override fun listIterator(index: Int): MutableListIterator<T> =
        object : MutableListIterator<T> {
            private val it = orig.listIterator(index)

            override fun hasNext() = it.hasNext()
            override fun next() = it.next()
            override fun hasPrevious() = it.hasPrevious()
            override fun previous() = it.previous()
            override fun nextIndex() = it.nextIndex()
            override fun previousIndex() = it.previousIndex()

            override fun add(element: T) = change { it.add(element) }
            override fun set(element: T) = change { it.set(element) }
            override fun remove() = change { it.remove() }
        }
}

class SimpleMutableState<T>(value: T, private val onChanged: (T) -> Unit) : MutableState<T> {
    override var value: T = value
        set(value) {
            field = value
            onChanged(value)
        }

    override fun component1(): T = value

    override fun component2(): (T) -> Unit = {
        value = it
    }

}


/**
 * Maps a state from the type [I] to the type [O]
 */
class MappedState<I, O>(private val orig: State<I>, private val mapper: (I) -> O) : State<O> {
    override val value: O get() = mapper(orig.value) // Important: reference orig.value on every referencing of this value
}