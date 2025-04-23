package io.github.natanfudge.fn.test.example.network.state

import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunContext
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.state.funValue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

@Disabled
class StateFunValueExamples {
    @Test
    fun funValueExample() {
        // Create a class that extends Fun and has a synchronized property using funValue
        class Player(id: String, client: FunContext) : Fun(id, client) {
            var health by funValue(100)
            var name by funValue("Player")
        }

        // Set up a multiplayer environment with two clients
        val multiplayer = LocalMultiplayer(2)
        val player1 = Player("player-1", multiplayer.clients[0])
        val player2 = Player("player-1", multiplayer.clients[1]) // Same ID to sync state

        // Verify initial values are synchronized
        assertEquals(100, player2.health)
        assertEquals("Player", player2.name)

        // Modify values on player1 and verify they sync to player2
        player1.health = 75
        player1.name = "Updated Player"

        assertEquals(75, player2.health)
        assertEquals("Updated Player", player2.name)

        // Modify values on player2 and verify they sync back to player1
        player2.health = 50
        player2.name = "Player Two"

        assertEquals(50, player1.health)
        assertEquals("Player Two", player1.name)
    }

    @Test
    fun testNewApproach() {
        // Create a class that extends Fun and has a synchronized property using funValue
        class Player(id: String, client: FunContext) : Fun(id, client) {
            var mana by funValue(100)
            var hp by funValue(50)
        }

        @PredictedRpc
        fun playCard(player: Player) {
            player.context.sendMessageToServer(TODO())
            player.mana--
            player.hp++
        }

        // Set up a multiplayer environment with two clients
        val multiplayer = LocalMultiplayer(2)
        val player1 = Player("player-1", multiplayer.clients[0])
        val player2 = Player("player-1", multiplayer.clients[1])
        val serverPlayer = Player("player-1", multiplayer.server)

        assertThrows<UnallowedFunException> {
            player2.mana = 2
        }



        // Verify initial values are synchronized
        assertEquals(100, player2.mana)

        playCard(player1)

        // Modify values on player1 and verify they sync to player2
//        serverPlayer.mana = 75

        assertEquals(99, player1.mana)
        assertEquals(99, player2.mana)
        assertEquals(101, player2.hp)
        assertEquals(101, player2.hp)

    }
}


annotation class PredictedRpc

//TODO: All state modifications should be done like this:
class ServerHandler {
    class Player(id: String, context: FunContext): Fun(id , context) {
        var mana: Int by funValue(100)
        var hp by funValue(50)
    }
    // The big thing here is going to be figuring out how to pass this Player thing.
    // As long as the Player is registered at some point in the server, we can fetch it out of a map.
    // We will need to add a @Serializable(with = FunSerializer). Compiler plugin should get rid of that.
    fun playCard(player: Player) {
        player.mana--
        player.hp++
    }
}
