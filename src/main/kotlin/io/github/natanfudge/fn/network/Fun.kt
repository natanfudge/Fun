package io.github.natanfudge.fn.network

import io.github.natanfudge.fn.network.state.MapStateHolder


/**
 * Base class for components that need to synchronize state between multiple clients in a multiplayer environment.
 *
 * Fun components automatically register themselves with a [FunStateManager] and use [funValue] properties
 * to synchronize state changes across all clients.
 *
 * @sample io.github.natanfudge.fn.example.network.NetworkExamples.networkStateExample
 */
abstract class Fun(
    /**
     * Unique identifier for this component. Components with the same ID across different clients
     * will synchronize their state.
     */
    val id: String,
    val context: FunContext,
) {
    init {
        context.stateManager.register(this, MapStateHolder())
    }
}
