package io.github.natanfudge.fn.test.example.network.state

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunClient
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.state.funSet
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StateFunSetExamples {
    @Test
    fun funSetExample() {
        // Create a class that extends Fun and has a synchronized set
        class Room(id: String, client: FunClient) : Fun(id, client) {
            val activeUsers = funSet<String>("activeUsers", "Admin")
        }

        // Set up a multiplayer environment with two clients
        val multiplayer = LocalMultiplayer(2)
        val room1 = Room("meeting-room", multiplayer.clients[0])
        val room2 = Room("meeting-room", multiplayer.clients[1]) // Same ID to sync state

        // Verify initial values are synchronized
        assertEquals(1, room2.activeUsers.size, "Initial set size should be synchronized")
        assertTrue(room2.activeUsers.contains("Admin"), "Initial user should be synchronized")

        // Add elements to the set on room1 and verify they sync to room2
        room1.activeUsers.add("User1")
        
        assertEquals(2, room2.activeUsers.size, "Set size should be synchronized after add")
        assertTrue(room2.activeUsers.contains("User1"), "Added user should be synchronized")
        
        // Modify the set on room2 and verify changes sync back to room1
        room2.activeUsers.add("User2")
        
        assertEquals(3, room1.activeUsers.size, "Set size should be synchronized after add from room2")
        assertTrue(room1.activeUsers.contains("User2"), "Added user from room2 should be synchronized")
        
        // Demonstrate other set operations
        room1.activeUsers.remove("User1")
        assertEquals(2, room2.activeUsers.size, "Set size should be synchronized after remove")
        assertFalse(room2.activeUsers.contains("User1"), "Removed user should not be present")
        
        // Add multiple elements at once
        val newUsers = setOf("User3", "User4")
        room2.activeUsers.addAll(newUsers)
        assertEquals(4, room1.activeUsers.size, "Set size should be synchronized after addAll")
        assertTrue(room1.activeUsers.contains("User3"), "New user should be synchronized")
        assertTrue(room1.activeUsers.contains("User4"), "New user should be synchronized")
        
        // Remove multiple elements at once
        room1.activeUsers.removeAll(setOf("User2", "User3"))
        assertEquals(2, room2.activeUsers.size, "Set size should be synchronized after removeAll")
        assertFalse(room2.activeUsers.contains("User2"), "Removed user should not be present")
        assertFalse(room2.activeUsers.contains("User3"), "Removed user should not be present")
        
        // Retain only specific elements
        room2.activeUsers.retainAll(setOf("Admin"))
        assertEquals(1, room1.activeUsers.size, "Set size should be synchronized after retainAll")
        assertTrue(room1.activeUsers.contains("Admin"), "Retained user should be present")
        assertFalse(room1.activeUsers.contains("User4"), "Non-retained user should not be present")
        
        // Clear the set
        room1.activeUsers.clear()
        assertEquals(0, room2.activeUsers.size, "Set should be empty after clear")
        assertTrue(room2.activeUsers.isEmpty(), "Set should be empty after clear")
    }
}