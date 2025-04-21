package io.github.natanfudge.fn.network.state

import io.github.natanfudge.fn.network.Fun
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
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
inline fun <reified T> Fun.funSet(name: String, vararg items: T): FunSet<T> = funSet(name, serializer(), mutableSetOf(*items))

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
    val list = FunSet(items, name, this, serializer)
    client.registerState(id, name, list)
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
) : MutableSet<T>, FunState {

    override fun equals(other: Any?): Boolean {
        return _items == other
    }

    override fun hashCode(): Int {
        return _items.hashCode()
    }

    override fun toString(): String  = "$name$_items"

    private val listSerializer = ListSerializer(serializer)


    private fun Collection<T>.toNetwork(): String {
        return (this as? List<T> ?: this.toList()).toNetwork(listSerializer)
    }

    private fun T.toNetwork() = toNetwork(serializer)
    override fun iterator(): MutableIterator<T> = _items.iterator()


    override fun add(element: T): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.CollectionAdd(element.toNetwork()))
        return _items.add(element)
    }

    override fun remove(element: T): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.CollectionRemove(element.toNetwork()))
        return _items.remove(element)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.CollectionAddAll(elements.toNetwork()))
        return _items.addAll(elements)
    }



    override fun removeAll(elements: Collection<T>): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.CollectionRemoveAll(elements.toNetwork()))
        return _items.removeAll(elements)
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.CollectionRetainAll(elements.toNetwork()))
        return _items.retainAll(elements)
    }

    override fun clear() {
        owner.client.sendUpdate(owner.id, name, StateChange.CollectionClear)
        _items.clear()
    }



    @Deprecated("Don't use this")
    override fun <T : Any?> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?>? {
        error("Bad")
    }

    override fun applyChange(change: StateChange) {
        when (change) {
            !is StateChange.SetOp -> warnMismatchingStateChange(change, "SET")
            StateChange.CollectionClear -> _items.clear()
            is StateChange.SingleChange -> {
                val value = change.value.decode(serializer)
                when (change) {
                    is StateChange.CollectionAdd -> _items.add(value)
                    is StateChange.CollectionRemove -> _items.remove(value)
                    else -> error("impossible")
                }
            }

            is StateChange.BulkChange -> {
                val values = change.values.decode(listSerializer)
                when (change) {
                    is StateChange.CollectionAddAll -> _items.addAll(values)
                    is StateChange.CollectionRemoveAll -> _items.removeAll(values)
                    is StateChange.CollectionRetainAll -> _items.retainAll(values)
                    else -> error("impossible")
                }
            }
        }
    }

    override val size: Int
        get() = _items.size

    override fun isEmpty(): Boolean  = _items.isEmpty()
    override fun contains(element: T): Boolean = _items.contains(element)

    override fun containsAll(elements: Collection<T>): Boolean = _items.containsAll(elements)


}
