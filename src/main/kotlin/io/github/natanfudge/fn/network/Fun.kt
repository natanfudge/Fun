package io.github.natanfudge.fn.network

/**
 * Base class for components that need to synchronize state between multiple clients in a multiplayer environment.
 * 
 * Fun components automatically register themselves with a [FunClient] and use [funState] properties
 * to synchronize state changes across all clients.
 * 
 * @sample io.github.natanfudge.fn.test.examples.network.NetworkExamples.networkStateExample
 */
abstract class Fun(
    /**
     * Unique identifier for this component. Components with the same ID across different clients
     * will synchronize their state.
     */
    val id: String,

    /**
     * The client that this component is connected to, responsible for sending and receiving state updates.
     */
    val client: FunClient,
){
    init {
        client.register(this, MapStateHolder())
    }
}
