package io.github.natanfudge.fn.network

import io.github.natanfudge.fn.network.state.StateChangeValue

//TODO: I think it makes sense to allow configuring what happens when you access a value you can't see.
// Either on a global level - throw / return default
// Or a Fun level - throw / return default
// Or a state level - throw / return default
data class ClientHandle(
    val permissions: ClientPermissions,
    val update: suspend (changes: List<StateChange>) -> Unit,
)

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

data class StateChange(val key: StateKey, val value: StateChangeValue)

/**
 * Creates a local multiplayer environment where multiple clients can communicate with each other.
 *
 * Contains multiple [FunClient] instances that send and receive state updates without network connections.
 *
 * @see Fun
 */
class LocalMultiplayer(
    /**
     * The number of clients/players to create in this local multiplayer environment.
     */
    private val playerCount: Int,
) {
    /**
     * List of clients that can be used to connect [Fun] components.
     * Each client has its own communication channel to other clients.
     */
    val clients: List<FunClient> = List(playerCount) { clientNum ->
        val communication = object : FunCommunication {
            override suspend fun send(
                changes: List<StateChange>,
            ) {
                val handle = handles[clientNum]
                propagateStateChange(handle, changes)
            }
        }
        FunClient(communication, name = "Local Multiplayer $clientNum")
    }

    private val handles = clients.map { client ->
        ClientHandle(
            ClientPermissions.Admin,
            update = {
                for ((key, value) in it) {
                    client.receiveUpdate(key, value)
                }
            }
        )
    }

    private suspend fun propagateStateChange(instigator: ClientHandle, changes: List<StateChange>) {
        handles.forEach { client ->
            if (client != instigator) {
                // Don't send information to clients that shouldn't have it
                val visibleState = changes.filter { client.permissions.canSee(it.key) }
                client.update(visibleState)
            }
        }
    }

//    private fun acceptMessage(
//        holderKey: String,
//        propertyKey: String,
//        change: StateChange,
//        clientHandle: ClientHandle,
//    ) {
////        if(clientHandle)
//    }
}

class CheatingException(message: String) : Exception(message)