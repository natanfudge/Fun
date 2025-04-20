@file:Suppress("UNCHECKED_CAST", "FunctionName", "PropertyName")

package io.github.natanfudge.fn.network

import io.github.natanfudge.fn.error.UnfunStateException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import java.util.function.IntFunction
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


//TODO: things to think about:
// 0. List-state and Map-state
// 1. Multithreading of state manager
// 2. 'secret' values - values only visible to their owner.
// 3. Protection of values - modifying values only from the server. Permission system - usually all permissions given to the server
// 4. Prediction - running server logic on the client for as long as possible
// 5. Merged client-server optimization - how can we reuse objects in case the client and server are running in the same process?
// 6. See how we can optimize object IDs in production to avoid a separate ID for each instance
// 7. Some sort of API Fun.child(id: String) that creates a child state of a Fun.
// 8. We could have a SinglePlayerFun that doesn't require specifying a client.
// 9. Compiler plugin: see compiler plugin.md
// 10. Think about how we are gonna pass Fun components through RPC methods


/**
 * Interface for sending state updates between clients.
 *
 * Implementations of this interface handle the communication between clients,
 * serializing values and ensuring they reach the appropriate destinations.
 *
 * @see Fun
 */
interface FunCommunication {
    /**
     * Sends a state update to other clients.
     *
     * [holderKey] and [propertyKey] identify which property is being updated,
     * while [value] contains the new state that should be synchronized.
     */
    fun <T> send(holderKey: String, propertyKey: String, change: StateChange<T>, serializer: KSerializer<T>)
}


/**
 * Manages the state synchronization for a single client in a multiplayer environment.
 *
 * The FunClient is responsible for:
 * - Registering Fun components and their state
 * - Sending state updates to other clients
 * - Receiving and applying state updates from other clients
 *
 * @see Fun
 */
class FunClient(
    /**
     * The communication channel used to send updates to other clients.
     */
    val communication: FunCommunication,
    val name: String = "FunClient",
) {

    private val stateHolders = mutableMapOf<String, MapStateHolder>()

    /**
     * Receives a state update from another client and applies it to the appropriate state holder.
     */
    internal fun receiveUpdate(holderKey: String, propertyKey: String, change: StateChange<NetworkValue>) {
        val holder = stateHolders[holderKey]
        if (holder != null) {
            holder.applyChange(propertyKey, change)
        } else {
            println("WARNING: Received a value to the Fun component '${holderKey}', but no such ID exists, so the value was discarded. (value = $change)")
        }
    }

    /**
     * Sends a state update to other clients through the communication channel.
     */
    internal fun <T> sendUpdate(
        holderKey: String,
        propertyKey: String,
        change: StateChange<T>,
        serializer: KSerializer<T>,
    ) {
        // SLOW: we can avoid serialization in case both clients are in the same process
        communication.send(holderKey, propertyKey, change, serializer)
    }

    /**
     * Registers a Fun component with this client, allowing it to send and receive state updates.
     */
    internal fun register(fn: Fun, state: MapStateHolder) {
        if (fn.id in stateHolders) {
            throw IllegalArgumentException("A state holder with the id '${fn.id}' was registered twice. Make sure to give Fun components unique IDs. ")
        }
        stateHolders[fn.id] = state
    }

    /**
     * Sets the value of [state] to the pending value if it exists.
     * A pending value will get DELETED once it is retrieved!
     */
    internal fun <T> setPendingValue(holderKey: String, propertyKey: String, state: PropertyState<T>) {
        val holder = stateHolders[holderKey] ?: throw UnfunStateException(
            "State holder '$holderKey' was not registered prior to attempting getting the pending value of its sub-state '$propertyKey'!"
        )
        holder.setPendingValue(propertyKey, state)
    }

    /**
     * Registers a state property with its parent state holder.
     */
    internal fun registerState(holderKey: String, propertyKey: String, state: PropertyState<Any?>) {
        val holder = stateHolders[holderKey] ?: throw UnfunStateException(
            "State holder '$holderKey' was not registered prior to registering its sub-state '$propertyKey'!"
        )
        holder.registerState(propertyKey, state)
    }
}

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
    private val pendingValues = mutableMapOf<String, StateChange.SetProperty<NetworkValue>>()

    private val map = mutableMapOf<String, PropertyState<Any?>>()

    /**
     * Updates the value of a property identified by [key].
     * If the property hasn't been registered yet, the value is stored as pending.
     */
    override fun applyChange(key: String, value: StateChange<NetworkValue>) {
        if (key in map) {
            val state = map.getValue(key)
            state.applyChange(value)
            // Property was properly registered, update it
//            map.getValue(key).receiveUpdate(value)
        } else {
            //TODO: verify we register lists ASAP so this is not a problem
            check(value is StateChange.SetProperty<NetworkValue>)
            // Not registered yet, wait for it to be registered to grant it the value
            pendingValues[key] = value
        }
    }


    /**
     * Registers a state property with this holder.
     */
    fun registerState(key: String, value: PropertyState<Any?>) {
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

private fun <T> StateChange<NetworkValue>.decode(serializer: KSerializer<T>): StateChange<T> {
    TODO()
}

/**
 * Interface for objects that can hold and update state properties.
 */
interface FunStateHolder {
    /**
     * Updates the value of a property identified by [key].
     */
    fun applyChange(key: String, change: StateChange<NetworkValue>)
}


/**
 * Creates a property delegate that automatically synchronizes its value across all clients.
 *
 * This function is used to create properties in [Fun] components that will be automatically
 * synchronized when their value changes.
 *
 * @see Fun
 */
inline fun <reified T> funState(value: T): PropertyState<T> = PropertyState(value, serializer())

interface FunState<T> {
    @InternalFunApi
    fun applyChange(change: StateChange<T>)

    val serializer: KSerializer<T>
}

fun <T> FunState<T>.applyChange(change: StateChange<NetworkValue>) = applyChange(change.decode(serializer))


/**
 * A property delegate that synchronizes its value across all clients in a multiplayer environment.
 *
 * When a FunState property is modified, the change is automatically sent to all other clients.
 * Similarly, when another client modifies the property, the change is automatically applied locally.
 *
 * @see Fun
 */
class PropertyState<T>(private var value: T, override val serializer: KSerializer<T>) : KoinComponent,
    ReadWriteProperty<Fun, T>, FunState<T> {
    private var registered: Boolean = false


    override fun toString(): String {
        return value.toString()
    }

    @InternalFunApi
    override fun applyChange(change: StateChange<T>) {
        require(change is StateChange.SetProperty<T>)
//        this.value = Json.decodeFromString(serializer, change.value)
        this.value = change.value
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
            change = StateChange.SetProperty(value),
//            value = value,
            serializer
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

//TODO: this [name] API is iffy
inline fun <reified T> Fun.funList(name: String = T::class.simpleName ?: "", vararg items: T): FunList<T> {
    return FunList(mutableListOf(*items), name, this, serializer())
}

class FunList<T> @PublishedApi internal constructor(
    @InternalFunApi val _items: MutableList<T>,
    private val name: String,
    private val owner: Fun,
    override val serializer: KSerializer<T>,
) : MutableList<T> by _items, FunState<T> {
//    @InternalFunApi
//    fun _setDirectly(index: Int, value: T) {
//        this.items[index] = value
//    }
//
//    @InternalFunApi
//    fun _addDirectly(value: T) {
//        this.items.add(value)
//    }
//
//    @InternalFunApi
//    fun _removeDirectly(value: T) {
//        this.items.remove(value)
//    }
//
//    @InternalFunApi
//    fun _addAllDirectly(values: Collection<T>) {
//        this.items.addAll(values)
//    }

    override fun add(element: T): Boolean {
        owner.client.sendUpdate(owner.id, name, StateChange.ListAdd(element), serializer)
        return _items.add(element)
    }

    override fun remove(element: T): Boolean {
        TODO()
    }

    override fun addAll(elements: Collection<T>): Boolean = TODO()

    override fun addAll(index: Int, elements: Collection<T>): Boolean = TODO()

    override fun removeAll(elements: Collection<T>): Boolean = TODO()
    override fun retainAll(elements: Collection<T>): Boolean = TODO()
    override fun clear(): Unit = TODO()

    override operator fun set(index: Int, element: T): T = TODO()
    override fun add(index: Int, element: T): Unit = TODO()

    override fun removeAt(index: Int): T = TODO()


    @Deprecated("Don't use this")
    override fun <T : Any?> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?>? {
        error("Bad")
    }

    override fun applyChange(change: StateChange<T>) {
        TODO("Not yet implemented")
    }

}

//fun FunList<T>


class World(id: String, client: FunClient) : Fun(id, client) {
    val coins = funList<Int>("coins")
}

class WorldAutoGen(id: String, client: FunClient) : Fun(id, client) {
    val coins = funList<Int>("coins")


    fun setValue(key: String, change: StateChange<NetworkValue>) {
        when (key) {
            "coins" -> {
                coins.applyChange(change)
//                when (change) {
//                    is StateChange.SingleChange<NetworkValue> -> {
//                        val deserialized = change.value.decode(Int.serializer())
//                        when (change) {
//                            is StateChange.ListSet -> coins._items[change.index] = deserialized
//                            is StateChange.SetProperty -> error("Unexpected SET-PROPERTY for list")
//                        }
//                    }
//
//                    is StateChange.BulkChange -> TODO()
//                }
            }

            else -> error("No such key $key")
        }
    }
}

fun <T> NetworkValue.decode(serializer: KSerializer<T>): T = TODO()

sealed interface StateChange<T> {
    sealed interface SingleChange<T> : StateChange<T> {
        val value: T
    }

    data class SetProperty<T>(override val value: T) : SingleChange<T>

    sealed interface BulkChange<T> : StateChange<T> {
        val values: T
    }

    data class ListSet<T>(override val value: T, val index: Int) : SingleChange<T>
    data class ListAdd<T>(override val value: T) : SingleChange<T>
}
