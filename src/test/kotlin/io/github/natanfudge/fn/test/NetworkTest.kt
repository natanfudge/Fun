//package io.github.natanfudge.fn.test
//
//import kotlin.test.assertEquals
//import io.github.natanfudge.fn.network.Fun
//import io.github.natanfudge.fn.network.FunStateContext
//import io.github.natanfudge.fn.network.LocalMultiplayer
//import io.github.natanfudge.fn.network.state.funValue
//import org.junit.jupiter.api.Test
//
///**
// * Examples demonstrating the usage of the Fun networking system.
// */
//class NetworkTest {
//    /**
//     * Demonstrates how to use the Fun networking system to synchronize state between multiple clients
//     * in a virtual trading card game. The test validates that state is properly synchronized
//     * between different clients when using the same Fun component ID.
//     */
//    @Test
//    fun networkStateExample() {
//        // Create a local multiplayer environment with 2 players
//        val multiplayer = LocalMultiplayer(2)
//
//        // Create a game session for player 1 (using client 0)
//        val clientA = CardGameSession("cosmic-duel", multiplayer.clients[0], "playerA")
//
//        // Create a game session for player 2 (using client 1)
//        val clientB = CardGameSession("cosmic-duel", multiplayer.clients[1], "playerB")
//
//        // Create a game session for server
//        val serverSession = CardGameSession("cosmic-duel", multiplayer.server, "server")
//
//        // Initially, both players have their starting resources
//        assertEquals(3, clientA.playerAMana, "playerA mana via clientA")
//        assertEquals(3, clientA.playerBMana, "playerB mana via clientA")
//        assertEquals(3, clientB.playerAMana, "playerA mana via clientB")
//        assertEquals(3, clientB.playerBMana, "playerB mana via clientB")
//        assertEquals("playerA", clientA.currentTurn, "Current turn via clientA")
//        assertEquals("playerA", clientB.currentTurn, "Current turn via clientB")
//
//        // Server simulates playerA playing a card that costs 2 mana during playerA's turn
//        serverSession.currentTurn = "playerA" // Ensure it's playerA's turn
//        serverSession.playCard("Cosmic Dragon", 2)
//
//        // Verify state is updated and synchronized across both clients
//        assertEquals(1, clientA.playerAMana, "playerA mana after playing card via clientA")
//        assertEquals(1, clientB.playerAMana, "playerA mana after playing card via clientB")
//        assertEquals("Cosmic Dragon", clientA.lastPlayedCard, "Last played card via clientA")
//        assertEquals("Cosmic Dragon", clientB.lastPlayedCard, "Last played card via clientB")
//
//        // Server ends playerA's turn
//        serverSession.endTurn()
//
//        // Verify turn changed
//        assertEquals("playerB", clientA.currentTurn, "Current turn after server ends playerA's turn (via clientA)")
//        assertEquals("playerB", clientB.currentTurn, "Current turn after server ends playerA's turn (via clientB)")
//
//        // Server simulates playerB playing a card during playerB's turn
//        serverSession.currentTurn = "playerB" // Ensure it's playerB's turn
//        serverSession.playCard("Nebula Shield", 1)
//
//        // Verify state is updated and synchronized
//        assertEquals(4, clientA.playerBMana, "playerB mana after playing card via clientA") // 3 + 2 - 1 = 4 mana left
//        assertEquals(4, clientB.playerBMana, "playerB mana after playing card via clientB")
//        assertEquals("Nebula Shield", clientA.lastPlayedCard, "Last played card via clientA")
//        assertEquals("Nebula Shield", clientB.lastPlayedCard, "Last played card via clientB")
//
//        // Server ends playerB's turn
//        serverSession.endTurn()
//
//        // Verify turn changed back to playerA and mana is increased
//        assertEquals("playerA", clientA.currentTurn, "Current turn after full round (via clientA)")
//        assertEquals("playerA", clientB.currentTurn, "Current turn after full round (via clientB)")
//        assertEquals(3, clientA.playerAMana, "playerA mana after turn cycle (via clientA)") // 1 + 2 = 3
//        assertEquals(3, clientB.playerAMana, "playerA mana after turn cycle (via clientB)")
//    }
//}
//
///**
// * A virtual trading card game session that extends Fun to demonstrate state synchronization.
// *
// * All clients connected with the same ID will share the same state. This means that
// * changes made by one client will be visible to all other clients.
// *
// * @param id The unique identifier for this game session
// * @param client The client that this session is running on
// * @param playerId The ID of the player using this client ("playerA" or "playerB")
// */
//class CardGameSession(
//    id: String,
//    client: FunStateContext,
//    private val playerId: String
//) : Fun(id, client) {
//    // Player resources
//    var playerAMana by funValue(3)
//    var playerBMana by funValue(3)
//
//    // Game state - shared by all clients
//    var currentTurn by funValue("playerA") // Initially playerA's turn
//    var lastPlayedCard by funValue("")
//
//    /**
//     * Simulates playing a card from hand, reducing mana and updating the last played card.
//     * Only works if it's the player's turn and they have enough mana.
//     * Server can play cards for any player.
//     */
//    fun playCard(cardName: String, manaCost: Int) {
//        // If this is the server, it can play cards for any player
//        // If this is a client, only allow the player to play a card on their turn
//        if (playerId != "server" && currentTurn != playerId) {
//            return
//        }
//
//        // Check if the current turn player has enough mana and reduce it
//        when (currentTurn) {
//            "playerA" -> {
//                if (playerAMana >= manaCost) {
//                    playerAMana -= manaCost
//                    lastPlayedCard = cardName
//                }
//            }
//            "playerB" -> {
//                if (playerBMana >= manaCost) {
//                    playerBMana -= manaCost
//                    lastPlayedCard = cardName
//                }
//            }
//        }
//    }
//
//    /**
//     * Ends the current player's turn and updates game state accordingly.
//     * Only works if it's the player's turn.
//     * Server can end turn for any player.
//     */
//    fun endTurn() {
//        // If this is the server, it can end turn for any player
//        // If this is a client, only allow the player to end the turn if it's their turn
//        if (playerId != "server" && currentTurn != playerId) {
//            return
//        }
//
//        // Switch turn to the other player
//        currentTurn = if (currentTurn == "playerA") "playerB" else "playerA"
//
//        // Give the new active player additional mana (2 per turn)
//        if (currentTurn == "playerA") {
//            playerAMana += 2
//        } else {
//            playerBMana += 2
//        }
//    }
//}
