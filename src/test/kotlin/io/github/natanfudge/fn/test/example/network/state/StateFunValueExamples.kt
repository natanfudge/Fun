package io.github.natanfudge.fn.test.example.network.state

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunClient
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.state.funValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StateFunValueExamples {
    @Test
    fun funValueExample() {
        // Create a class that extends Fun and has a synchronized property using funValue
        class Player(id: String, client: FunClient) : Fun(id, client) {
            var health by funValue(100)
            var name by funValue("Player")
        }

        // Set up a multiplayer environment with two clients
        val multiplayer = LocalMultiplayer(2)
        val player1 = Player("player-1", multiplayer.clients[0])
        val player2 = Player("player-1", multiplayer.clients[1]) // Same ID to sync state

        // Verify initial values are synchronized
        assertEquals(100, player2.health, "Initial health should be synchronized")
        assertEquals("Player", player2.name, "Initial name should be synchronized")

        // Modify values on player1 and verify they sync to player2
        player1.health = 75
        player1.name = "Updated Player"
        
        assertEquals(75, player2.health, "Health should be synchronized after update")
        assertEquals("Updated Player", player2.name, "Name should be synchronized after update")
        
        // Modify values on player2 and verify they sync back to player1
        player2.health = 50
        player2.name = "Player Two"
        
        assertEquals(50, player1.health, "Health should be synchronized after update from player2")
        assertEquals("Player Two", player1.name, "Name should be synchronized after update from player2")
    }
}