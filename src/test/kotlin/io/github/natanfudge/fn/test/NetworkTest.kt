package io.github.natanfudge.fn.test

import kotlin.test.assertEquals
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunClient
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.funState
import org.junit.jupiter.api.Test
//TODO: test doesn't pass
/**
 * Examples demonstrating the usage of the Fun networking system.
 */
class NetworkTest {
    /**
     * Demonstrates how to use the Fun networking system to synchronize state between multiple clients
     * in a virtual trading card game.
     */
    @Test
    fun networkStateExample() {
        // Create a local multiplayer environment with 2 players
        val multiplayer = LocalMultiplayer(2)

        // Create a game session for player 1
        val player1Session = CardGameSession("cosmic-duel", multiplayer.clients[0])

        // Create a game session for player 2 with the same ID to ensure state synchronization
        val player2Session = CardGameSession("cosmic-duel", multiplayer.clients[1])

        // Initially, both players have their starting resources
        assertEquals(3, player1Session.playerMana, "player1Session.playerMana")
        assertEquals(3, player2Session.playerMana, "player2Session.playerMana")
        assertEquals(3, player1Session.opponentMana, "player1Session.opponentMana")
        assertEquals(3, player2Session.opponentMana, "player2Session.opponentMana")

        // Player 1 plays a card that costs 2 mana
        player1Session.playCard("Cosmic Dragon", 2)

        // Verify player 1's state is updated
        assertEquals(1, player1Session.playerMana, "player1Session.playerMana") // 3 - 2 = 1 mana left
        assertEquals("Cosmic Dragon", player1Session.lastPlayedCard, "player1Session.lastPlayedCard")

        // Verify player 2 sees the changes
        assertEquals(3, player2Session.opponentMana, "player2Session.opponentMana") // Player 1's mana is Player 2's opponent's mana
        assertEquals("Cosmic Dragon", player2Session.opponentLastPlayedCard, "player2Session.opponentLastPlayedCard")

        // Player 2 responds by playing their own card
        player2Session.playCard("Nebula Shield", 1)

        // Verify both players see the updated game state
        assertEquals(2, player1Session.opponentMana, "player1Session.opponentMana") // 3 - 1 = 2 mana left for player 2
        assertEquals("Nebula Shield", player1Session.opponentLastPlayedCard, "player1Session.opponentLastPlayedCard")
        assertEquals(2, player2Session.playerMana, "player2Session.playerMana")
        assertEquals("Nebula Shield", player2Session.lastPlayedCard, "player2Session.lastPlayedCard")

        // Player 1 ends their turn, which gives player 2 a card and increases their mana
        player1Session.endTurn()

        // Verify turn end effects are synchronized
        assertEquals("player2", player1Session.currentTurn, "player1Session.currentTurn")
        assertEquals("player2", player2Session.currentTurn, "player2Session.currentTurn")
        assertEquals(4, player2Session.playerMana, "player2Session.playerMana") // 2 + 2 = 4 (base gain of 2 per turn)
        assertEquals(4, player1Session.opponentMana, "player1Session.opponentMana")
    }
}

/**
 * A virtual trading card game session that extends Fun to demonstrate state synchronization.
 */
class CardGameSession(id: String, client: FunClient) : Fun(id, client) {
    // Player's resources and state
    var playerMana by funState(3)
    var lastPlayedCard by funState("")

    // Opponent's resources and state (as seen by this client)
    var opponentMana by funState(3)
    var opponentLastPlayedCard by funState("")

    // Game state
    var currentTurn by funState("player1")

    /**
     * Simulates playing a card from hand, reducing mana and updating the last played card.
     */
    fun playCard(cardName: String, manaCost: Int) {
        if (playerMana >= manaCost) {
            playerMana -= manaCost
            lastPlayedCard = cardName
        }
    }

    /**
     * Ends the current player's turn and updates game state accordingly.
     */
    fun endTurn() {
        // Switch the turn to the other player
        currentTurn = if (currentTurn == "player1") "player2" else "player1"

        // In a real game, we would need to determine which client is which player
        // For this example, we'll simply update both mana values for demonstration
        // In a real implementation, you would use client-specific logic
        if (currentTurn == "player1") {
            // It's player 1's turn now, so increase their mana
            playerMana += 2
        } else {
            // It's player 2's turn now, so increase their mana
            opponentMana += 2
        }
    }
}
