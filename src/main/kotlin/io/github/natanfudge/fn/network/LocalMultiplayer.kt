package io.github.natanfudge.fn.network

import io.github.natanfudge.fn.core.ClientHandle
import io.github.natanfudge.fn.core.FunClient
import io.github.natanfudge.fn.core.FunCommunication
import io.github.natanfudge.fn.core.FunServer
import io.github.natanfudge.fn.core.StateSyncPolicy
import io.github.natanfudge.fn.network.state.NetworkValue
import io.github.natanfudge.fn.network.state.StateChangeValue

//IDEA: I think it makes sense to allow configuring what happens when you access a value you can't see.
// Either on a global level - throw / return current
// Or a Fun level - throw / return current
// Or a state level - throw / return current
data class ServerHeldClientHandle(
    val permissions: ClientPermissions,
    val update: suspend (changes: List<StateChange>) -> Unit,
) : ClientHandle

/**
 * A unique identifier for any state in the application
 */
data class StateKey(
    val holder: String,
    val property: String,
)

sealed interface ClientPermissions {
    fun canSee(state: StateKey): Boolean

    object Admin : ClientPermissions {
        override fun canSee(state: StateKey): Boolean {
            return true
        }
    }

    object None : ClientPermissions {
        override fun canSee(state: StateKey): Boolean {
            return false
        }
    }
}

data class StateChange(val key: StateKey, val value: StateChangeValue, val policy: StateSyncPolicy)

/**
 * Creates a local multiplayer environment where multiple clients can communicate with each other.
 *
 * Contains multiple [io.github.natanfudge.fn.core.FunStateManager] instances that send and receive state updates without network connections.
 *
 * @see io.github.natanfudge.fn.core.Fun
 */
class LocalMultiplayer(
    /**
     * The number of clients/players to create in this local multiplayer environment.
     */
    playerCount: Int,
) {
    val server = FunServer(
        synchronousUpdates = true
    ) {
        propagateStateChange(it)
    }

    /**
     * List of clients that can be used to connect [io.github.natanfudge.fn.core.Fun] components.
     * Each client has its own communication channel to other clients.
     */
    val clients: List<FunClient> = List(playerCount) { clientNum ->
        FunClient(object : FunCommunication {

            override fun send(message: NetworkValue) {
                error("to do")
            }

        })
    }
//    name = "Local Multiplayer $clientNum"

    private val handles = clients.map { client ->
        ServerHeldClientHandle(
            ClientPermissions.Admin,
            update = {
                for ((key, value) in it) {
                    client.receiveUpdate(key, value)
                }
            }
        )
    }

    private suspend fun propagateStateChange(changes: List<StateChange>) {
        handles.forEach { client ->
            // Don't send information to clients that shouldn't have it (such as the one that caused the change)
            val visibleState = changes.filter { it.policy.syncTo(client) }
            client.update(visibleState)
        }
    }
}

class CheatingException(message: String) : Exception(message)