package io.github.natanfudge.fn.network.state

import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.core.useOldStateIfPossible
import io.github.natanfudge.fn.network.StateKey
import io.github.natanfudge.fn.network.state.StateChangeValue.*
import io.github.natanfudge.fn.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.util.function.IntFunction



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
    @InternalFunApi var _items: MutableSet<T>,
    private val name: String,
    ownerId: FunId,
    private val serializer: KSerializer<T>,
    override val editor: ValueEditor<Set<T>>,
) : MutableSet<T>, FunState<Set<T>> {
    val changed = EventStream.create<SetOp>("setChanged")

//        override

    override fun beforeChange(callback: (Set<T>) -> Unit): Listener<Set<T>> {
        return changed.listenUnscoped("setChanged") {
            callback(_items)
        }.cast()
    }

    override var value: Set<T>
        get() = _items
        set(value) {
            _items = value.toMutableSet()
            changed(SetProperty(value))
        }

    private val key = StateKey(ownerId, name)

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
        error(" TO DO")
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
