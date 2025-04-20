@file:Suppress("UNCHECKED_CAST", "FunctionName", "PropertyName")

package io.github.natanfudge.fn.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import java.util.function.IntFunction
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


//TODO: things to think about:
// 0. FunMap and then FunSet (which is just a wrapper over FunMap)
// 1. Multithreading of state manager
// 2. 'secret' values - values only visible to their owner.
// 3. Protection of values - modifying values only from the server. Permission system - usually all permissions given to the server
// 4. Prediction - running server logic on the client for as long as possible
// 4b. Prediction + server correction/rubberbanding. How do we allow prediction while avoiding incorrect values?
// 5. Merged client-server optimization - how can we reuse objects in case the client and server are running in the same process?
// 6. See how we can optimize object IDs in production to avoid a separate ID for each instance
// 7. Some sort of API Fun.child(id: String) that creates a child state of a Fun.
// 8. We could have a SinglePlayerFun that doesn't require specifying a client.
// 9. Compiler plugin: see compiler plugin.md
// 10. Think about how we are gonna pass Fun components through RPC methods

fun <T> Fun.funList(name: String, serializer: KSerializer<T>, items: MutableList<T>): FunList<T> {
    val list = FunList(items, name, this, serializer)
    client.registerState(id, name, list)
    return list
}


inline fun <reified T> Fun.funList(name: String, vararg items: T): FunList<T> = funList(name, serializer(), mutableListOf(*items))

/**
 * Creates a property delegate that automatically synchronizes its value across all clients.
 *
 * This function is used to create properties in [Fun] components that will be automatically
 * synchronized when their value changes.
 *
 * @see Fun
 */
inline fun <reified T> funState(value: T): PropertyState<T> = PropertyState(value, serializer())


/**
 * Represents a serialized value that can be sent over the network.
 */
internal typealias NetworkValue = String


/**
 * Default implementation of [FunStateHolder] that stores state in a map.
 */
internal class MapStateHolder : FunStateHolder {
    // Values that were sent to an object but the object did not have a chance to react to them yet,
    // because he did not try getting/setting the value yet.
    // This is mostly because of the limitation that we only get the key information from
    // ReadWriteProperty#getValue / setValue, and only at that point we can start registering the state holders.
    private val pendingValues = mutableMapOf<String, StateChange.SetProperty>()

    private val map = mutableMapOf<String, FunState>()

    /**
     * Updates the value of a property identified by [key].
     * If the property hasn't been registered yet, the value is stored as pending.
     */
    override fun applyChange(key: String, value: StateChange) {
        if (key in map) {
            val state = map.getValue(key)
            state.applyChange(value)
        } else {
            //TODO: verify we register lists ASAP so this is not a problem
            check(value is StateChange.SetProperty) { "The list with the key $key was unexpectedly not registered prior to having a change applied to it" }
            // Not registered yet, wait for it to be registered to grant it the value
            pendingValues[key] = value
        }
    }


    /**
     * Registers a state property with this holder.
     */
    fun registerState(key: String, value: FunState) {
        map[key] = value
    }

    /**
     * Sets a pending value to a state property if one exists.
     */
    fun <T> setPendingValue(key: String, state: PropertyState<T>) {
        if (key !in pendingValues) return
        val networkValue = pendingValues.getValue(key)
        state.applyChange(networkValue)
        pendingValues.remove(key)
    }
}


/**
 * Interface for objects that can hold and update state properties.
 */
interface FunStateHolder {
    /**
     * Updates the value of a property identified by [key].
     */
    fun applyChange(key: String, change: StateChange)
}


interface FunState {
    @InternalFunApi
    fun applyChange(change: StateChange)
}


/**
 * A property delegate that synchronizes its value across all clients in a multiplayer environment.
 *
 * When a FunState property is modified, the change is automatically sent to all other clients.
 * Similarly, when another client modifies the property, the change is automatically applied locally.
 *
 * @see Fun
 */
class PropertyState<T>(private var value: T, private val serializer: KSerializer<T>) : KoinComponent,
    ReadWriteProperty<Fun, T>, FunState {
    private var registered: Boolean = false


    override fun toString(): String {
        return value.toString()
    }

    @InternalFunApi
    override fun applyChange(change: StateChange) {
        require(change is StateChange.SetProperty)
        this.value = change.value.decode(serializer)
    }

    /**
     * Gets the current value of the property.
     *
     * On first access, the property is registered with the client and any pending
     * updates are applied.
     */
    override fun getValue(thisRef: Fun, property: KProperty<*>): T {
        if (!registered) {
            registered = true
            thisRef.client.registerState(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as PropertyState<Any?>
            )
            thisRef.client.setPendingValue(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as PropertyState<Any?>
            )
        }

        return value
    }

    /**
     * Sets a new value for the property and synchronizes it with all other clients.
     *
     * On first access, the property is registered with the client.
     */
    override fun setValue(thisRef: Fun, property: KProperty<*>, value: T) {
        if (!registered) {
            registered = true
            thisRef.client.registerState(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as PropertyState<Any?>
            )
        }

        this.value = value

        thisRef.client.sendUpdate(
            holderKey = thisRef.id,
            propertyKey = property.name,
            change = StateChange.SetProperty(value.toNetwork(serializer)),
        )
    }
}

// API: enable this in the future
//@RequiresOptIn(
//    level = RequiresOptIn.Level.WARNING,
//    message = "This API is only intended to be used by generated code. You should never access this directly."
//)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
@Retention(AnnotationRetention.BINARY)
annotation class InternalFunApi


class FunList<T> @PublishedApi internal constructor(
    @InternalFunApi val _items: MutableList<T>,
    private val name: String,
    private val owner: Fun,
    private val serializer: KSerializer<T>,
) : MutableList<T> by _items, FunState {

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

    override fun add(element: T): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.ListAdd(element.toNetwork()))
        return _items.add(element)
    }

    override fun remove(element: T): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.ListRemove(element.toNetwork()))
        return _items.remove(element)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.ListAddAll(elements.toNetwork()))
        return _items.addAll(elements)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        // We need to include the index in the network update
        owner.client.sendUpdate(owner.id, name, StateChange.ListIndexedAddAll(elements.toNetwork(), index))
        return _items.addAll(index, elements)
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.ListRemoveAll(elements.toNetwork()))
        return _items.removeAll(elements)
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.ListRetainAll(elements.toNetwork()))
        return _items.retainAll(elements)
    }

    override fun clear() {
        owner.client.sendUpdate(owner.id, name, StateChange.ListClear)
        _items.clear()
    }

    override operator fun set(index: Int, element: T): T {
        owner.client.sendUpdate(owner.id, name, StateChange.ListSet(element.toNetwork(), index))
        return _items.set(index, element)
    }

    override fun add(index: Int, element: T) {
        owner.client.sendUpdate(owner.id, name, StateChange.ListIndexedAdd(element.toNetwork(), index))
        _items.add(index, element)
    }

    override fun removeAt(index: Int): T {
        owner.client.sendUpdate(owner.id, name, StateChange.ListRemoveAt(index))
        return _items.removeAt(index)
    }


    @Deprecated("Don't use this")
    override fun <T : Any?> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?>? {
        error("Bad")
    }

    override fun applyChange(change: StateChange) {
        when (change) {
            StateChange.ListClear -> _items.clear()
            is StateChange.ListRemoveAt -> _items.removeAt(change.index)
            is StateChange.SingleChange -> {
                val value = change.value.decode(serializer)
                when (change) {
                    is StateChange.ListAdd -> _items.add(value)
                    is StateChange.ListIndexedAdd -> _items.add(change.index, value)
                    is StateChange.ListRemove -> _items.remove(value)
                    is StateChange.ListSet -> _items[change.index] = value
                    is StateChange.SetProperty -> println(
                        "Warning - Mismatching message sender and receiver - sender attempted SET-PROPERTY, " +
                                "but receiver (this client) holds LIST. The message will be ignored (set to ${change.value})."
                    )
                }
            }

            is StateChange.BulkChange -> {
                val values = change.values.decode(listSerializer)
                when (change) {
                    is StateChange.ListAddAll -> _items.addAll(values)
                    is StateChange.ListIndexedAddAll -> _items.addAll(change.index, values)
                    is StateChange.ListRemoveAll -> _items.removeAll(values)
                    is StateChange.ListRetainAll -> _items.retainAll(values)
                }
            }
        }
    }

}


fun <T> NetworkValue.decode(serializer: KSerializer<T>): T = Json.decodeFromString(serializer, this)
private fun <T> T.toNetwork(serializer: KSerializer<T>) = Json.encodeToString(serializer, this)

sealed interface StateChange {
    sealed interface SingleChange : StateChange {
        val value: NetworkValue
    }

    data class SetProperty(override val value: NetworkValue) : SingleChange

    sealed interface BulkChange : StateChange {
        val values: NetworkValue
    }

    data class ListSet(override val value: NetworkValue, val index: Int) : SingleChange
    data class ListAdd(override val value: NetworkValue) : SingleChange
    data class ListIndexedAdd(override val value: NetworkValue, val index: Int) : SingleChange
    data class ListRemove(override val value: NetworkValue) : SingleChange
    data class ListRemoveAt(val index: Int) : StateChange

    data class ListAddAll(override val values: NetworkValue) : BulkChange
    data class ListIndexedAddAll(override val values: NetworkValue, val index: Int) : BulkChange
    data class ListRemoveAll(override val values: NetworkValue) : BulkChange
    data class ListRetainAll(override val values: NetworkValue) : BulkChange
    object ListClear : StateChange
}
