package io.github.natanfudge.fn.test.examples.network

import kotlin.test.assertEquals
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunClient
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.funState
import org.junit.jupiter.api.Test








class NetworkExamples {
    @Test
    fun networkStateExample() {
        class World(id: String, client: FunClient) : Fun(id, client) {
            var width by funState(100)
        }

        val multiplayer = LocalMultiplayer(2)
        val client1World = World("my-world", multiplayer.clients[0])
        val client2World = World("my-world", multiplayer.clients[1])

        assertEquals(100, client2World.width, "client2World.width")
        client1World.width = 1000
        assertEquals(1000, client2World.width, "client2World.width")
        assertEquals(1000, client1World.width, "client1World.width")
        client2World.width = 500
        assertEquals(500, client2World.width, "client2World.width")
        assertEquals(500, client1World.width, "client1World.width")
    }
}
