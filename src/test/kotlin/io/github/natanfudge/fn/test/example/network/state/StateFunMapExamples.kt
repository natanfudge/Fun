package io.github.natanfudge.fn.test.example.network.state

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunStateContext
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.state.funMap
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StateFunMapExamples {
    @Test
    fun funMapExample() {
        // Create a class that extends Fun and has a synchronized map
        class ScoreBoard(id: String, client: FunStateContext) : Fun(id, client) {
            val playerScores = funMap<String, Int>("playerScores", "Player1" to 100)
        }

        // Set up a multiplayer environment with two clients
        val multiplayer = LocalMultiplayer(2)
        val scoreBoard1 = ScoreBoard("score-board", multiplayer.clients[0])
        val scoreBoard2 = ScoreBoard("score-board", multiplayer.clients[1]) // Same ID to sync state
        val serverScoreBoard = ScoreBoard("score-board", multiplayer.server) // Server instance for state changes

        // Verify initial values are synchronized
        assertEquals(1, scoreBoard2.playerScores.size)
        assertEquals(100, scoreBoard2.playerScores["Player1"])

        // Add entries to the map on server and verify they sync to clients
        serverScoreBoard.playerScores["Player2"] = 200

        assertEquals(2, scoreBoard1.playerScores.size)
        assertEquals(200, scoreBoard1.playerScores["Player2"])
        assertEquals(2, scoreBoard2.playerScores.size)
        assertEquals(200, scoreBoard2.playerScores["Player2"])

        // Modify the map on server and verify changes sync to clients
        serverScoreBoard.playerScores["Player3"] = 300
        serverScoreBoard.playerScores["Player1"] = 150 // Update existing entry

        assertEquals(3, scoreBoard1.playerScores.size)
        assertEquals(150, scoreBoard1.playerScores["Player1"])
        assertEquals(300, scoreBoard1.playerScores["Player3"])
        assertEquals(3, scoreBoard2.playerScores.size)
        assertEquals(150, scoreBoard2.playerScores["Player1"])
        assertEquals(300, scoreBoard2.playerScores["Player3"])

        // Demonstrate other map operations
        serverScoreBoard.playerScores.remove("Player2")
        assertEquals(2, scoreBoard1.playerScores.size)
        assertFalse(scoreBoard1.playerScores.containsKey("Player2"))
        assertEquals(2, scoreBoard2.playerScores.size)
        assertFalse(scoreBoard2.playerScores.containsKey("Player2"))

        // Add multiple entries at once
        val newScores = mapOf("Player4" to 400, "Player5" to 500)
        serverScoreBoard.playerScores.putAll(newScores)
        assertEquals(4, scoreBoard1.playerScores.size)
        assertEquals(400, scoreBoard1.playerScores["Player4"])
        assertEquals(500, scoreBoard1.playerScores["Player5"])
        assertEquals(4, scoreBoard2.playerScores.size)
        assertEquals(400, scoreBoard2.playerScores["Player4"])
        assertEquals(500, scoreBoard2.playerScores["Player5"])

        // Clear the map
        serverScoreBoard.playerScores.clear()
        assertEquals(0, scoreBoard1.playerScores.size)
        assertTrue(scoreBoard1.playerScores.isEmpty())
        assertEquals(0, scoreBoard2.playerScores.size)
        assertTrue(scoreBoard2.playerScores.isEmpty())
    }
}
