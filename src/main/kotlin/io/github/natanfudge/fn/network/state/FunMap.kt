@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.network.state

import io.github.natanfudge.fn.core.FunOld
import io.github.natanfudge.fn.network.StateKey
import io.github.natanfudge.fn.core.sendStateChange
import io.github.natanfudge.fn.util.ImmutableCollection
import io.github.natanfudge.fn.util.ImmutableSet
import io.github.natanfudge.fn.util.Listener
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer


/**
 * Creates a synchronized map that automatically propagates changes to all clients.
 * 
 * This function creates a [FunMap] with the specified name and initial items. The map's
 * contents will be automatically synchronized across all clients in the multiplayer environment.
 * 
 * This version uses Kotlin's reified type parameters to automatically determine the serializers
 * for both keys and values.
 * 
 * @sample io.github.natanfudge.fn.test.example.network.state.StateFunMapExamples.funMapExample
 * @see FunMap
 * @see funSet
 * @see funList
 */
inline fun <reified K, reified V> FunOld.funMap(name: String, vararg items: Pair<K,V>): FunMap<K, V> =
    funMap(name, getFunSerializer(), getFunSerializer(), mutableMapOf(*items))


inline fun <reified K, reified V> FunOld.funMap(name: String, items: Map<K,V>): FunMap<K, V> =
    funMap(name, getFunSerializer(), getFunSerializer(), items.toMutableMap())

/**
 * Creates a synchronized map that automatically propagates changes to all clients.
 * 
 * This function creates a [FunMap] with the specified name, serializers, and initial items.
 * The map's contents will be automatically synchronized across all clients in the multiplayer environment.
 * 
 * Use this version when you need to specify custom serializers for keys and values.
 * 
 * @see FunMap
 * @see funSet
 * @see funList
 */
fun <K, V> FunOld.funMap(name: String, keySerializer: KSerializer<K>, valueSerializer: KSerializer<V>, items: MutableMap<K, V>): FunMap<K, V> {
    val map = FunMap(useOldStateIfPossible(items,this.id,name), name, this, keySerializer, valueSerializer)
    context.stateManager.registerState(id, name, map)
    return map
}


/**
 * A synchronized map that automatically propagates changes to all clients.
 * 
 * FunMap implements the standard [MutableMap] interface, allowing it to be used
 * like any other map. However, any modifications to the map (putting, removing entries)
 * are automatically synchronized across all clients in the multiplayer environment.
 * 
 * Create instances using the [funMap] extension function on [FunOld].
 * 
 * Example usage:
 * ```
 * val playerScores = fun.funMap<String, Int>("playerScores")
 * playerScores["player1"] = 100 // Automatically synchronized to all clients
 * ```
 * 
 * Note: The [keys], [values], and [entries] collections returned by this map are immutable
 * to prevent unsynchronized modifications. Use the map's methods directly to modify the map.
 * 
 * @see funMap
 * @see FunList
 * @see FunSet
 */
class FunMap<K, V> @PublishedApi internal constructor(
    @InternalFunApi val _items: MutableMap<K, V>,
    private val name: String,
    private val owner: FunOld,
    private val keySerializer: KSerializer<K>,
    private val valueSerializer: KSerializer<V>,
) : MutableMap<K, V> , FunState<Map<K,V>> {

    override fun beforeChange(callback: (Map<K, V>) -> Unit): Listener<Map<K, V>> {
        TODO("Not yet implemented")
    }


    override var value: Map<K,V>
        get() = _items
        set(value) {
            _items.clear()
            _items.putAll(value)
        }
    
    private val key = StateKey(owner.id, name)

    override fun equals(other: Any?): Boolean {
        return _items == other
    }

    override fun hashCode(): Int {
        return _items.hashCode()
    }

    override fun toString(): String = "$name$_items"

    private val mapSerializer = MapSerializer(keySerializer, valueSerializer)

    private fun K.keyToNetwork() = toNetwork(keySerializer)
    private fun V.valueToNetwork() = toNetwork(valueSerializer)
    private fun Map<out K, V>.mapToNetwork(): NetworkValue = this@mapToNetwork.toNetwork(mapSerializer as KSerializer<Map<out K, V>>)

    // Don't allow modifying the results of these functions because we don't synchronize them
    override val keys: MutableSet<K> = ImmutableSet(_items.keys)
    override val values: MutableCollection<V> = ImmutableCollection(_items.values)
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = ImmutableSet(_items.entries)

    override fun put(key: K, value: V): V? {
        //TODO: do this only in a ServerFunValue
        owner.context.sendStateChange(this.key, StateChangeValue.MapPut(key.keyToNetwork(), value.valueToNetwork()))
        return _items.put(key, value)
    }

    override fun remove(key: K): V? {
        owner.context.sendStateChange(this.key, StateChangeValue.MapRemove(key.keyToNetwork()))
        return _items.remove(key)
    }

    override fun putAll(from: Map<out K, V>) {
        owner.context.sendStateChange(key, StateChangeValue.MapPutAll(from.mapToNetwork()))
        _items.putAll(from)
    }

    override fun clear() {
        owner.context.sendStateChange(key, StateChangeValue.CollectionClear)
        _items.clear()
    }

    override val size: Int
        get() =_items.size

    override fun isEmpty(): Boolean = _items.isEmpty()

    override fun containsKey(key: K): Boolean  = _items.containsKey(key)
    override fun containsValue(value: V): Boolean = _items.containsValue(value)

    override fun get(key: K): V? = _items[key]
    override fun applyChange(change: StateChangeValue) {
        when (change) {
            !is StateChangeValue.MapOp -> warnMismatchingStateChange(change, "MAP")
            StateChangeValue.CollectionClear -> _items.clear()
            is StateChangeValue.MapRemove -> {
                val key = change.key.decode(keySerializer)
                _items.remove(key)
            }
            is StateChangeValue.MapPut -> {
                val key = change.key.decode(keySerializer)
                val value = change.value.decode(valueSerializer)
                _items[key] = value
            }
            is StateChangeValue.MapPutAll -> {
                val entries = change.entries.decode(mapSerializer)
                _items.putAll(entries)
            }
        }
    }
}
