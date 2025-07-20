package io.github.natanfudge.fn.network.state

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.sendStateChange
import io.github.natanfudge.fn.network.StateKey
import io.github.natanfudge.fn.network.state.StateChangeValue.*
import io.github.natanfudge.fn.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.util.function.IntFunction

/**
 * Creates a synchronized set that automatically propagates changes to all clients.
 *
 * This function creates a [FunSet] with the specified name and initial items. The set's
 * contents will be automatically synchronized across all clients in the multiplayer environment.
 *
 * This version uses Kotlin's reified type parameters to automatically determine the serializer.
 *
 * @sample io.github.natanfudge.fn.test.example.network.state.StateFunSetExamples.funSetExample
 * @see FunSet
 * @see funMap
 * @see funList
 */
inline fun <reified T> Fun.funSet(name: String, vararg items: T): FunSet<T> = funSet(name, getFunSerializer(), mutableSetOf(*items))
inline fun <reified T> Fun.funSet(vararg items: T): Delegate<FunSet<T>> = obtainPropertyName {
    funSet(it, getFunSerializer(), mutableSetOf(*items))
}

/**
 * Creates a synchronized set that automatically propagates changes to all clients.
 *
 * This function creates a [FunSet] with the specified name, serializer, and initial items.
 * The set's contents will be automatically synchronized across all clients in the multiplayer environment.
 *
 * Use this version when you need to specify a custom serializer.
 *
 * @see FunSet
 * @see funMap
 * @see funList
 */
fun <T> Fun.funSet(name: String, serializer: KSerializer<T>, items: MutableSet<T>): FunSet<T> {
    val list = FunSet(useOldStateIfPossible(items, name), name, this, serializer)
    context.stateManager.registerState(id, name, list)
    return list
}

/**
 * A synchronized set that automatically propagates changes to all clients.
 *
 * FunSet implements the standard [MutableSet] interface, allowing it to be used
 * like any other set. However, any modifications to the set (adding, removing elements)
 * are automatically synchronized across all clients in the multiplayer environment.
 *
 * Create instances using the [funSet] extension function on [Fun].
 *
 * Example usage:
 * ```
 * val players = fun.funSet<Player>("players")
 * players.add(newPlayer) // Automatically synchronized to all clients
 * ```
 *
 * @see funSet
 * @see FunList
 * @see FunMap
 */
class FunSet<T> @PublishedApi internal constructor(
    @InternalFunApi val _items: MutableSet<T>,
    private val name: String,
    private val owner: Fun,
    private val serializer: KSerializer<T>,
) : MutableSet<T>, FunState<Set<T>> {

    val changed by owner.event<SetOp>()

//        override

    override fun beforeChange(callback: (Set<T>) -> Unit): Listener<Set<T>> {
        return changed.listenUnscoped {
            callback(_items)
        }.cast()
    }

    override var value: Set<T>
        get() = _items
        set(value) {
            _items.clear()
            _items.addAll(value)
        }

    private val key = StateKey(owner.id, name)

    override fun equals(other: Any?): Boolean {
        return _items == other
    }

    override fun hashCode(): Int {
        return _items.hashCode()
    }

    override fun toString(): String = "$name$_items"

    private val listSerializer = ListSerializer(serializer)


    private fun Collection<T>.toNetwork(): String {
        return (this as? List<T> ?: this.toList()).toNetwork(listSerializer)
    }

    private fun T.toNetwork() = toNetwork(serializer)
    override fun iterator(): MutableIterator<T> = _items.iterator()

    override fun add(element: T): Boolean = _items.add(element).also { changed(CollectionAdd(element)) }

    override fun remove(element: T) = _items.remove(element).also { changed(CollectionAdd(element)) }

    override fun addAll(elements: Collection<T>) = _items.addAll(elements).also { changed(CollectionAddAll(elements)) }


    @Suppress("ConvertArgumentToSet")
    override fun removeAll(elements: Collection<T>) = _items.removeAll(elements).also { changed(CollectionRemoveAll(elements)) }
    @Suppress("ConvertArgumentToSet")
    override fun retainAll(elements: Collection<T>) = _items.retainAll(elements).also { changed(CollectionRetainAll(elements)) }

    override fun clear() {
        _items.clear()
        changed(CollectionClear)
    }


    @Deprecated("Don't use this")
    override fun <T : Any?> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?>? {
        error("Bad")
    }

    @Suppress("UNCHECKED_CAST")
    override fun applyChange(change: StateChangeValue) {
        TODO()
//        when (change) {
//            !is SetOp -> warnMismatchingStateChange(change, "SET")
//            CollectionClear -> _items.clear()
//            is StateChangeValue.SingleChange<*> -> {
//                when (change) {
//                    is CollectionAdd<*> -> _items.add(change.value as T)
//                    is CollectionRemove -> _items.remove(value)
//                    else -> error("impossible")
//                }
//            }
//
//            is StateChangeValue.BulkChange -> {
//                val values = change.values.decode(listSerializer)
//                when (change) {
//                    is StateChangeValue.CollectionAddAll -> _items.addAll(values)
//                    is StateChangeValue.CollectionRemoveAll -> _items.removeAll(values)
//                    is StateChangeValue.CollectionRetainAll -> _items.retainAll(values)
//                    else -> error("impossible")
//                }
//            }
//        }
    }

    override val size: Int
        get() = _items.size

    override fun isEmpty(): Boolean = _items.isEmpty()
    override fun contains(element: T): Boolean = _items.contains(element)

    override fun containsAll(elements: Collection<T>): Boolean = _items.containsAll(elements)


}
