package io.github.natanfudge.fn.network

import io.github.natanfudge.fn.error.UnfunStateException
import io.github.natanfudge.fn.network.state.FunState
import io.github.natanfudge.fn.network.state.FunValue
import io.github.natanfudge.fn.network.state.MapStateHolder
import io.github.natanfudge.fn.network.state.StateChangeValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


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
    suspend fun send(changes: List<StateChange>/*, serializer: KSerializer<T>*/)
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
    internal fun receiveUpdate(key: StateKey, change: StateChangeValue) {
        val holder = stateHolders[key.holder]
        if (holder != null) {
            holder.applyChange(key.property, change)
        } else {
            println("WARNING: Received a value to the Fun component '${key.holder}', but no such ID exists, so the value was discarded. (value = $change)")
        }
    }

    // DANGER: We might need to setup this differently and close it in some way
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Sends a state update to other clients through the communication channel.
     */
    internal fun sendUpdate(
        key: StateKey,
//        holderKey: String,
//        propertyKey: String,
        change: StateChangeValue,
    ) {
        // TODO: this is not how I want to do it. It should be added to a queue SYNCHRONOUSLY, and then processed in batches
        scope.launch {
            communication.send(listOf(StateChange(key, change)))
        }
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
    internal fun <T> setPendingValue(holderKey: String, propertyKey: String, state: FunValue<T>) {
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
