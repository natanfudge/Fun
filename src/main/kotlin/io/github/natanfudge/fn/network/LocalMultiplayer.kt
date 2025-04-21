package io.github.natanfudge.fn.network

import io.github.natanfudge.fn.network.state.StateChange

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
    private val playerCount: Int
) {
    /**
     * List of clients that can be used to connect [Fun] components.
     * Each client has its own communication channel to other clients.
     */
    val clients: List<FunClient> = List(playerCount) { clientNum ->
        val communication = object : FunCommunication {
            override fun  send(
                holderKey: String,
                propertyKey: String,
                change: StateChange,
//                serializer: KSerializer<T>
            ) {
//                val asJson = Json.encodeToString(serializer, value)

                repeat(playerCount) {
                    if (clientNum != it) {
                        clients[it].receiveUpdate(holderKey, propertyKey, change)
                    }
                }
            }
        }
        FunClient(communication, name = "Local Multiplayer $clientNum")
    }
}
