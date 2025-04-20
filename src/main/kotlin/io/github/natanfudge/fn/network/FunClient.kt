package io.github.natanfudge.fn.network

import io.github.natanfudge.fn.error.UnfunStateException


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
    fun send(holderKey: String, propertyKey: String, change: StateChange/*, serializer: KSerializer<T>*/)
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
    internal fun receiveUpdate(holderKey: String, propertyKey: String, change: StateChange) {
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
    internal fun sendUpdate(
        holderKey: String,
        propertyKey: String,
        change: StateChange,
    ) {
        // SLOW: we can avoid serialization in case both clients are in the same process
        communication.send(holderKey, propertyKey, change/*, serializer*/)
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
     internal fun registerState(holderKey: String, propertyKey: String, state: FunState) {
        val holder = stateHolders[holderKey] ?: throw UnfunStateException(
            "State holder '$holderKey' was not registered prior to registering its sub-state '$propertyKey'!"
        )
        holder.registerState(propertyKey, state)
    }
}
