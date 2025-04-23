package io.github.natanfudge.fn.network.state

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.StateKey
import io.github.natanfudge.fn.network.sendStateChange
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import java.util.function.IntFunction

/**
 * Creates a synchronized list that automatically propagates changes to all clients.
 * 
 * This function creates a [FunList] with the specified name and initial items. The list's
 * contents will be automatically synchronized across all clients in the multiplayer environment.
 * 
 * This version uses Kotlin's reified type parameters to automatically determine the serializer.
 * 
 * @sample io.github.natanfudge.fn.test.example.network.state.StateFunListExamples.funListExample
 * @see FunList
 * @see funMap
 * @see funSet
 */
inline fun <reified T> Fun.funList(name: String, vararg items: T): FunList<T> = funList(name, serializer(), mutableListOf(*items))

/**
 * Creates a synchronized list that automatically propagates changes to all clients.
 * 
 * This function creates a [FunList] with the specified name, serializer, and initial items.
 * The list's contents will be automatically synchronized across all clients in the multiplayer environment.
 * 
 * Use this version when you need to specify a custom serializer.
 * 
 * @see FunList
 * @see funMap
 * @see funSet
 */
fun <T> Fun.funList(name: String, serializer: KSerializer<T>, items: MutableList<T>): FunList<T> {
    val list = FunList(items, name, this, serializer)
    context.stateManager.registerState(id, name, list)
    return list
}

/**
 * A synchronized list that automatically propagates changes to all clients.
 * 
 * FunList implements the standard [MutableList] interface, allowing it to be used
 * like any other list. However, any modifications to the list (adding, removing, setting elements)
 * are automatically synchronized across all clients in the multiplayer environment.
 * 
 * Create instances using the [funList] extension function on [Fun].
 * 
 * Example usage:
 * ```
 * val messages = fun.funList<String>("messages")
 * messages.add("Hello") // Automatically synchronized to all clients
 * messages[0] = "Updated message" // Also synchronized
 * ```
 * 
 * Note: The [listIterator] methods are not supported as they would allow unsynchronized modifications.
 * Use the list's direct methods instead.
 * 
 * @see funList
 * @see FunMap
 * @see FunSet
 */
class FunList<T> @PublishedApi internal constructor(
    @InternalFunApi val _items: MutableList<T>,
    private val name: String,
    private val owner: Fun,
    private val serializer: KSerializer<T>,
) : MutableList<T>, FunState {
    
    private val key = StateKey(owner.id, name)

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
        owner.context.sendStateChange(key, StateChangeValue.CollectionAdd(element.toNetwork()))
        return _items.add(element)
    }

    override fun remove(element: T): Boolean {
        owner.context.sendStateChange(key, StateChangeValue.CollectionRemove(element.toNetwork()))
        return _items.remove(element)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        owner.context.sendStateChange(key, StateChangeValue.CollectionAddAll(elements.toNetwork()))
        return _items.addAll(elements)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        // We need to include the index in the network update
        owner.context.sendStateChange(key, StateChangeValue.ListIndexedAddAll(elements.toNetwork(), index))
        return _items.addAll(index, elements)
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        owner.context.sendStateChange(key, StateChangeValue.CollectionRemoveAll(elements.toNetwork()))
        return _items.removeAll(elements)
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        owner.context.sendStateChange(key, StateChangeValue.CollectionRetainAll(elements.toNetwork()))
        return _items.retainAll(elements)
    }

    override fun clear() {
        owner.context.sendStateChange(key, StateChangeValue.CollectionClear)
        _items.clear()
    }

    override operator fun set(index: Int, element: T): T {
        owner.context.sendStateChange(key, StateChangeValue.ListSet(element.toNetwork(), index))
        return _items.set(index, element)
    }

    override fun add(index: Int, element: T) {
        owner.context.sendStateChange(key, StateChangeValue.ListIndexedAdd(element.toNetwork(), index))
        _items.add(index, element)
    }

    override fun removeAt(index: Int): T {
        owner.context.sendStateChange(key, StateChangeValue.ListRemoveAt(index))
        return _items.removeAt(index)
    }

    override fun listIterator(): MutableListIterator<T> {
        error("A MutableListIterator is not supported for a FunList yet. If you need this feature feel free to open an issue")
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        error("A MutableListIterator is not supported for a FunList yet. If you need this feature feel free to open an issue")
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        return _items.subList(fromIndex, toIndex)
    }


    @Deprecated("Don't use this")
    override fun <T : Any?> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?>? {
        error("Bad")
    }

    override fun applyChange(change: StateChangeValue) {
        when (change) {
            !is StateChangeValue.ListOp -> warnMismatchingStateChange(change, "LIST")
            StateChangeValue.CollectionClear -> _items.clear()
            is StateChangeValue.ListRemoveAt -> _items.removeAt(change.index)
            is StateChangeValue.SingleChange -> {
                val value = change.value.decode(serializer)
                when (change) {
                    is StateChangeValue.CollectionAdd -> _items.add(value)
                    is StateChangeValue.ListIndexedAdd -> _items.add(change.index, value)
                    is StateChangeValue.CollectionRemove -> _items.remove(value)
                    is StateChangeValue.ListSet -> _items[change.index] = value
                    else -> error("Impossible")
                }
            }

            is StateChangeValue.BulkChange -> {
                val values = change.values.decode(listSerializer)
                when (change) {
                    is StateChangeValue.CollectionAddAll -> _items.addAll(values)
                    is StateChangeValue.ListIndexedAddAll -> _items.addAll(change.index, values)
                    is StateChangeValue.CollectionRemoveAll -> _items.removeAll(values)
                    is StateChangeValue.CollectionRetainAll -> _items.retainAll(values)
                }
            }
        }
    }

    override val size: Int
        get() = _items.size

    override fun isEmpty(): Boolean  = _items.isEmpty()
    override fun contains(element: T): Boolean = _items.contains(element)

    override fun containsAll(elements: Collection<T>): Boolean = _items.containsAll(elements)

    override fun get(index: Int): T = _items[index]

    override fun indexOf(element: T): Int = _items.indexOf(element)

    override fun lastIndexOf(element: T): Int = _items.lastIndexOf(element)
}
