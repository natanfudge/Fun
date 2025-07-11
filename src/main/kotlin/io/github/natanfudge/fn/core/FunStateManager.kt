package io.github.natanfudge.fn.core

import androidx.compose.runtime.mutableStateMapOf
import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.error.UnfunStateException
import io.github.natanfudge.fn.network.StateChange
import io.github.natanfudge.fn.network.StateKey
import io.github.natanfudge.fn.network.state.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer


/**
 * Interface for sending state updates between clients.
 *
 * Implementations of this interface handle the communication between clients,
 * serializing values and ensuring they reach the appropriate destinations.
 *
 * @see FunOld
 */
interface FunStateSynchronizer {
    /**
     * Sends a state update to other clients.
     *
     * [holderKey] and [propertyKey] identify which property is being updated,
     * while [value] contains the new state that should be synchronized.
     */
    fun send(changes: List<StateChange>)

    object FromClient : FunStateSynchronizer {
        override fun send(
            changes: List<StateChange>,
//            instigator: ClientHandle
        ) {
            throw UnallowedFunException("This state was declared to be synchronized, so it should only be updated in a ServerLike context.")
        }
    }
}

//data class FunStateConfig(
//
//    val synchronousUpdates: Boolean = false,
//)

interface ClientHandle

interface StateSyncPolicy {
    fun syncTo(client: ClientHandle): Boolean

    object KnownToAll : StateSyncPolicy {
        override fun syncTo(client: ClientHandle): Boolean {
            return true
        }
    }

    object Private : StateSyncPolicy {
        override fun syncTo(client: ClientHandle): Boolean {
            return false
        }
    }
}

interface FunStateContext {
    companion object {
        fun isolatedClient() = FunClient.isolated()
    }

    fun sendStateChange(
        change: StateChange,
    )

    fun sendMessageToServer(function: String, parameters: List<SerializableValue<*>>)
    fun sendMessageToServer(function: String, vararg parameters: SerializableValue<*>) {
        sendMessageToServer(function, parameters.toList())
    }

    val stateManager: FunStateManager
}

//object RpcUtils {
//    fun sendMessageToServer(values: List<Any?>, serializers: List<K>)
//}

data class SerializableValue<T>(
    val value: T,
    val serializer: KSerializer<T>,
)


// LOWPRIO: policy should not have a default
fun FunStateContext.sendStateChange(key: StateKey, value: StateChangeValue, policy: StateSyncPolicy = StateSyncPolicy.KnownToAll) {
    sendStateChange(StateChange(key, value, policy))
}

class FunServer(
    /**
     * If true, updates in state will synchronize synchronously, meaning changing state will stall until all other clients have received the update.
     * This should only be used in local environments where there's no latency that will cause serious lag.
     */
    private val synchronousUpdates: Boolean,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val sendFunc: suspend (List<StateChange>) -> Unit,
) : FunStateContext {
    override val stateManager: FunStateManager = FunStateManager()
    override fun sendStateChange(
        change: StateChange,
    ) {
        if (synchronousUpdates) {
            runBlocking {
                sendFunc(listOf(change))
            }
        } else {
            // SLOW: this is not how I want to do it. It should be added to a queue SYNCHRONOUSLY, and then processed in batches
            scope.launch {
                sendFunc(listOf(change))
            }
        }
    }

    override fun sendMessageToServer(function: String, values: List<SerializableValue<*>>) {
        throw UnallowedFunException("You are not supposed to send a message from the server to the server. Might be a good idea to make this a no-op though.")
    }
}

class FunClient(internal val comm: FunCommunication) : FunStateContext {
    companion object {
        fun isolated() = FunClient(object : FunCommunication {
            override fun send(message: NetworkValue) {

            }
        })
    }

    override fun sendStateChange(
        change: StateChange,
    ) {
        // SUS: clients should not be sending state changes
//        throw UnallowedFunException("This state was declared to be synchronized, so it should only be updated in a ServerLike context.")
    }

    //LOWPRIO: I'm not sure how to strcture the client/server interacctions. I need to see a game in action with the engine first to see how things will go.
    // It might make sense to initate interactions FROM the server and therefore there's
    override fun sendMessageToServer(function: String, values: List<SerializableValue<*>>) {
        val rpc = Rpc(function, values.map { it.value })
        val serializer = Rpc.serializer(values.map { it.serializer })
        comm.send(error("to do"))
    }

    override val stateManager = FunStateManager()

    internal fun receiveUpdate(key: StateKey, change: StateChangeValue) {
        val holder = stateManager.stateHolders[key.holder]
        if (holder != null) {
            holder.applyChange(key.property, change)
        } else {
            println("WARNING: Received a value to the Fun component '${key.holder}', but no such ID exists, so the value was discarded. (value = $change)")
        }
    }
}

internal data class Rpc(
    val function: String,
    val parameters: List<Any?>,
) {
    companion object {
        fun serializer(parameterSerializers: List<KSerializer<*>>): KSerializer<Rpc> {
            error("to do, actually don't think i'm gonna do rpc manually")
        }
    }
}

//class ClientHeldClientHandle

interface FunCommunication {
    fun send(message: NetworkValue)
}

/**
 * Manages the state synchronization for a single client in a multiplayer environment.
 *
 * The FunClient is responsible for:
 * - Registering Fun components and their state
 * - Sending state updates to other clients
 * - Receiving and applying state updates from other clients
 *
 * @see FunOld
 */
class FunStateManager(
//    val synchronizer: FunStateSynchronizer,
//    val name: String = "FunStateManager",
) {

    internal val stateHolders = mutableStateMapOf<FunId, MapStateHolder>()

    fun getState(id: FunId): FunStateHolder? = stateHolders[id]

    /**
     * Receives a state update from another client and applies it to the appropriate state holder.
     */


    /**
     * Registers a Fun component with this client, allowing it to send and receive state updates.
     */
    internal fun register(fn: FunId, allowReregister: Boolean) {
        if (fn in stateHolders) {
            if (allowReregister) {
                return
            } else {
                throw IllegalArgumentException("A state holder with the id '${fn}' was registered twice. Make sure to give Fun components unique IDs. ")
            }
        }
        stateHolders[fn] = MapStateHolder()
    }

    internal fun unregister(fn: FunOld) {
        stateHolders.remove(fn.id)
    }

//    /**
//     * Sets the value of [state] to the pending value if it exists.
//     * A pending value will get DELETED once it is retrieved!
//     */
//    internal fun <T> setToPendingValue(holderKey: String, propertyKey: String, state: FunValue<T>) {
//        val holder = stateHolders[holderKey] ?: throw UnfunStateException(
//            "State holder '$holderKey' was not registered prior to attempting getting the pending value of its sub-state '$propertyKey'!"
//        )
//        holder.setPendingValue(propertyKey, state)
//    }

    /**
     * Registers a state property with its parent state holder.
     */
    internal fun registerState(holderKey: String, propertyKey: String, state: FunState<*>) {
        val holder = stateHolders[holderKey] ?: throw UnfunStateException(
            "State holder '$holderKey' was not registered prior to registering its sub-state '$propertyKey'!"
        )
        holder.registerState(propertyKey, state)
    }
}
