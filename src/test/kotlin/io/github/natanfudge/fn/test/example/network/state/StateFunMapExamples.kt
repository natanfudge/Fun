package io.github.natanfudge.fn.test.example.network.state

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunClient
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
        class ScoreBoard(id: String, client: FunClient) : Fun(id, client) {
            val playerScores = funMap<String, Int>("playerScores", "Player1" to 100)
        }

        // Set up a multiplayer environment with two clients
        val multiplayer = LocalMultiplayer(2)
        val scoreBoard1 = ScoreBoard("score-board", multiplayer.clients[0])
        val scoreBoard2 = ScoreBoard("score-board", multiplayer.clients[1]) // Same ID to sync state

        // Verify initial values are synchronized
        assertEquals(1, scoreBoard2.playerScores.size, "Initial map size should be synchronized")
        assertEquals(100, scoreBoard2.playerScores["Player1"], "Initial score should be synchronized")

        // Add entries to the map on scoreBoard1 and verify they sync to scoreBoard2
        scoreBoard1.playerScores["Player2"] = 200
        
        assertEquals(2, scoreBoard2.playerScores.size, "Map size should be synchronized after put")
        assertEquals(200, scoreBoard2.playerScores["Player2"], "Added score should be synchronized")
        
        // Modify the map on scoreBoard2 and verify changes sync back to scoreBoard1
        scoreBoard2.playerScores["Player3"] = 300
        scoreBoard2.playerScores["Player1"] = 150 // Update existing entry
        
        assertEquals(3, scoreBoard1.playerScores.size, "Map size should be synchronized after put from scoreBoard2")
        assertEquals(150, scoreBoard1.playerScores["Player1"], "Updated score should be synchronized")
        assertEquals(300, scoreBoard1.playerScores["Player3"], "Added score from scoreBoard2 should be synchronized")
        
        // Demonstrate other map operations
        scoreBoard1.playerScores.remove("Player2")
        assertEquals(2, scoreBoard2.playerScores.size, "Map size should be synchronized after remove")
        assertFalse(scoreBoard2.playerScores.containsKey("Player2"), "Removed entry should not be present")
        
        // Add multiple entries at once
        val newScores = mapOf("Player4" to 400, "Player5" to 500)
        scoreBoard2.playerScores.putAll(newScores)
        assertEquals(4, scoreBoard1.playerScores.size, "Map size should be synchronized after putAll")
        assertEquals(400, scoreBoard1.playerScores["Player4"], "New score should be synchronized")
        assertEquals(500, scoreBoard1.playerScores["Player5"], "New score should be synchronized")
        
        // Clear the map
        scoreBoard1.playerScores.clear()
        assertEquals(0, scoreBoard2.playerScores.size, "Map should be empty after clear")
        assertTrue(scoreBoard2.playerScores.isEmpty(), "Map should be empty after clear")
    }
}