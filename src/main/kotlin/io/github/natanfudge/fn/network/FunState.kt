@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.network

import io.github.natanfudge.fn.error.UnfunStateException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
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
    fun <T> send(holderKey: String, propertyKey: String, value: T, serializer: KSerializer<T>)
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
    val communication: FunCommunication
) {

    private val stateHolders = mutableMapOf<String, MapStateHolder>()

    /**
     * Receives a state update from another client and applies it to the appropriate state holder.
     */
    internal fun receiveUpdate(holderKey: String, propertyKey: String, value: NetworkValue) {
        val holder = stateHolders[holderKey]
        if (holder != null) {
            holder.setValue(propertyKey, value)
        } else {
            println("WARNING: Received a value to the Fun component '${holderKey}', but no such ID exists, so the value was discarded. (value = $value)")
        }
    }

    /**
     * Sends a state update to other clients through the communication channel.
     */
    internal fun <T> sendUpdate(holderKey: String, propertyKey: String, value: T, serializer: KSerializer<T>) {
        // SLOW: we can avoid serialization in case both clients are in the same process
        communication.send(holderKey, propertyKey, value, serializer)
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
    internal fun <T> setPendingValue(holderKey: String, propertyKey: String, state: FunState<T>) {
        val holder = stateHolders[holderKey] ?: throw UnfunStateException(
            "State holder '$holderKey' was not registered prior to attempting getting the pending value of its sub-state '$propertyKey'!"
        )
        holder.setPendingValue(propertyKey, state)
    }

    /**
     * Registers a state property with its parent state holder.
     */
    internal fun registerState(holderKey: String, propertyKey: String, state: FunState<Any?>) {
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
    private val pendingValues = mutableMapOf<String, NetworkValue>()

    private val map = mutableMapOf<String, FunState<Any?>>()

    /**
     * Updates the value of a property identified by [key].
     * If the property hasn't been registered yet, the value is stored as pending.
     */
    override fun setValue(key: String, value: NetworkValue) {
        if (key in map) {
            // Property was properly registered, update it
            map.getValue(key).receiveUpdate(value)
        } else {
            // Not registered yet, wait for it to be registered to grant it the value
            pendingValues[key] = value
        }
    }

    /**
     * Registers a state property with this holder.
     */
    fun registerState(key: String, value: FunState<Any?>) {
        map[key] = value
    }

    /**
     * Sets a pending value to a state property if one exists.
     */
    fun <T> setPendingValue(key: String, state: FunState<T>) {
        if (key !in pendingValues) return
        val networkValue = pendingValues.getValue(key)
        state.receiveUpdate(networkValue)
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
    fun setValue(key: String, value: NetworkValue)
}


/**
 * Creates a property delegate that automatically synchronizes its value across all clients.
 * 
 * This function is used to create properties in [Fun] components that will be automatically
 * synchronized when their value changes.
 * 
 * @see Fun
 */
inline fun <reified T> funState(value: T): FunState<T> = FunState(value, serializer())


/**
 * A property delegate that synchronizes its value across all clients in a multiplayer environment.
 * 
 * When a FunState property is modified, the change is automatically sent to all other clients.
 * Similarly, when another client modifies the property, the change is automatically applied locally.
 * 
 * @see Fun
 */
class FunState<T>(private var value: T, private val serializer: KSerializer<T>) : KoinComponent,
    ReadWriteProperty<Fun, T> {
    private var registered: Boolean = false

    /**
     * Updates the local value from a serialized network value.
     */
    internal fun receiveUpdate(value: NetworkValue) {
        this.value = Json.decodeFromString(serializer, value)
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
                state = this as FunState<Any?>
            )
            thisRef.client.setPendingValue(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunState<Any?>
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
                state = this as FunState<Any?>
            )
        }

        this.value = value

        thisRef.client.sendUpdate(
            holderKey = thisRef.id,
            propertyKey = property.name,
            value = value,
            serializer
        )
    }
}
