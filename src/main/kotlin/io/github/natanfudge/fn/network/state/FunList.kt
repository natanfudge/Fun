package io.github.natanfudge.fn.network.state

import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.network.StateKey
import io.github.natanfudge.fn.core.sendStateChange
import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.util.Listener
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
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
//inline fun <reified T> Fun.funList(name: String, vararg items: T): FunList<T> = funList(name, mutableListOf(*items))
//inline fun <reified T> Fun.funList(name: String, size: Int, init: (Int) -> T): FunList<T> = funList(name, MutableList(size, init))
//inline fun <reified T> Fun.funList(name: String, items: MutableList<T>): FunList<T> {
//    if (Fun::class.java.isAssignableFrom(T::class.java)) throw UnallowedFunException("funList is not intended for Fun values, use listOfFuns for that.")
//    return funList(name, getFunSerializer(), items)
//}
///**
// * Creates a synchronized list that automatically propagates changes to all clients.
// *
// * This function creates a [FunList] with the specified name, serializer, and initial items.
// * The list's contents will be automatically synchronized across all clients in the multiplayer environment.
// *
// * Use this version when you need to specify a custom serializer.
// *
// * @see FunList
// * @see funMap
// * @see funSet
// */
//fun <T> Fun.funList(name: String, serializer: KSerializer<T>, items: MutableList<T>): FunList<T> {
//    val list = FunList(useOldStateIfPossible(items,name), name, this, serializer)
//    context.stateManager.registerState(id, name, list)
//    return list
//}

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
    private val ownerId: FunId,
    private val serializer: KSerializer<T>,
    override val editor: ValueEditor<List<T>>,
) : MutableList<T>, FunState<List<T>> {

    override fun beforeChange(callback: (List<T>) -> Unit): Listener<List<T>> {
        throw NotImplementedError("Not yet implemented")
    }
    
    private val key = StateKey(ownerId, name)

    override fun equals(other: Any?): Boolean {
        return _items == other
    }

    override fun hashCode(): Int {
        return _items.hashCode()
    }

    override fun toString(): String  = "$name$_items"

    private val listSerializer = ListSerializer(serializer)


//    private fun Collection<T>: String {
//        return (this as? List<T> ?: this.toList()).toNetwork(listSerializer)
//    }

//    private fun T = toNetwork(serializer)
    override fun iterator(): MutableIterator<T> = _items.iterator()


    override fun add(element: T): Boolean {
        //TO DO: do this only in a ServerFunValue

        //TO DO: to restore this, add a _changed like in FunSet
//        owner.context.sendStateChange(key, StateChangeValue.CollectionAdd(element))
        return _items.add(element)
    }

    override fun remove(element: T): Boolean {
//        owner.context.sendStateChange(key, StateChangeValue.CollectionRemove(element))
        return _items.remove(element)
    }

    override fun addAll(elements: Collection<T>): Boolean {
//        owner.context.sendStateChange(key, StateChangeValue.CollectionAddAll(elements))
        return _items.addAll(elements)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        // We need to include the index in the network update
//        owner.context.sendStateChange(key, StateChangeValue.ListIndexedAddAll(elements, index))
        return _items.addAll(index, elements)
    }

    override fun removeAll(elements: Collection<T>): Boolean {
//        owner.context.sendStateChange(key, StateChangeValue.CollectionRemoveAll(elements))
        return _items.removeAll(elements)
    }

    override fun retainAll(elements: Collection<T>): Boolean {
//        owner.context.sendStateChange(key, StateChangeValue.CollectionRetainAll(elements))
        return _items.retainAll(elements)
    }

    override fun clear() {
//        owner.context.sendStateChange(key, StateChangeValue.CollectionClear)
        _items.clear()
    }

    override operator fun set(index: Int, element: T): T {
//        owner.context.sendStateChange(key, StateChangeValue.ListSet(element, index))
        return _items.set(index, element)
    }

    override fun add(index: Int, element: T) {
//        owner.context.sendStateChange(key, StateChangeValue.ListIndexedAdd(element, index))
        _items.add(index, element)
    }

    override fun removeAt(index: Int): T {
//        owner.context.sendStateChange(key, StateChangeValue.ListRemoveAt(index))
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
        error(" TO DO")
//        when (change) {
//            !is StateChangeValue.ListOp -> warnMismatchingStateChange(change, "LIST")
//            StateChangeValue.CollectionClear -> _items.clear()
//            is StateChangeValue.ListRemoveAt -> _items.removeAt(change.index)
//            is StateChangeValue.SingleChange -> {
//                val value = change.value.decode(serializer)
//                when (change) {
//                    is StateChangeValue.CollectionAdd -> _items.add(value)
//                    is StateChangeValue.ListIndexedAdd -> _items.add(change.index, value)
//                    is StateChangeValue.CollectionRemove -> _items.remove(value)
//                    is StateChangeValue.ListSet -> _items[change.index] = value
//                    else -> error("Impossible")
//                }
//            }
//
//            is StateChangeValue.BulkChange -> {
//                val values = change.values.decode(listSerializer)
//                when (change) {
//                    is StateChangeValue.CollectionAddAll -> _items.addAll(values)
//                    is StateChangeValue.ListIndexedAddAll -> _items.addAll(change.index, values)
//                    is StateChangeValue.CollectionRemoveAll -> _items.removeAll(values)
//                    is StateChangeValue.CollectionRetainAll -> _items.retainAll(values)
//                }
//            }
//        }
    }

    override var value: List<T>
        get() = _items
        set(value) {
            _items.clear()
            _items.addAll(value)
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
