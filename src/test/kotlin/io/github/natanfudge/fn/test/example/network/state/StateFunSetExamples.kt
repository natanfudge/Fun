package io.github.natanfudge.fn.test.example.network.state

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunStateContext
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.state.funSet
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun main() {
    println(Int.MIN_VALUE)
}

class StateFunSetExamples {

    @Test
    fun funSetExample() {
        // Create a class that extends Fun and has a synchronized set
        class Room(id: String, client: FunStateContext) : Fun(id, client) {
            val activeUsers = funSet<String>("activeUsers", "Admin")
        }

        // Set up a multiplayer environment with two clients
        val multiplayer = LocalMultiplayer(2)
        val room1 = Room("meeting-room", multiplayer.clients[0])
        val room2 = Room("meeting-room", multiplayer.clients[1]) // Same ID to sync state
        val serverRoom = Room("meeting-room", multiplayer.server) // Server instance for state changes

        // Verify initial values are synchronized
        assertEquals(1, room2.activeUsers.size)
        assertTrue(room2.activeUsers.contains("Admin"))

        // Add elements to the set on server and verify they sync to clients
        serverRoom.activeUsers.add("User1")

        assertEquals(2, room1.activeUsers.size)
        assertTrue(room1.activeUsers.contains("User1"))
        assertEquals(2, room2.activeUsers.size)
        assertTrue(room2.activeUsers.contains("User1"))

        // Add more elements on server and verify changes sync to clients
        serverRoom.activeUsers.add("User2")

        assertEquals(3, room1.activeUsers.size)
        assertTrue(room1.activeUsers.contains("User2"))
        assertEquals(3, room2.activeUsers.size)
        assertTrue(room2.activeUsers.contains("User2"))

        // Demonstrate other set operations
        serverRoom.activeUsers.remove("User1")
        assertEquals(2, room1.activeUsers.size)
        assertFalse(room1.activeUsers.contains("User1"))
        assertEquals(2, room2.activeUsers.size)
        assertFalse(room2.activeUsers.contains("User1"))

        // Add multiple elements at once
        val newUsers = setOf("User3", "User4")
        serverRoom.activeUsers.addAll(newUsers)
        assertEquals(4, room1.activeUsers.size)
        assertTrue(room1.activeUsers.contains("User3"))
        assertTrue(room1.activeUsers.contains("User4"))
        assertEquals(4, room2.activeUsers.size)
        assertTrue(room2.activeUsers.contains("User3"))
        assertTrue(room2.activeUsers.contains("User4"))

        // Remove multiple elements at once
        serverRoom.activeUsers.removeAll(setOf("User2", "User3"))
        assertEquals(2, room1.activeUsers.size)
        assertFalse(room1.activeUsers.contains("User2"))
        assertFalse(room1.activeUsers.contains("User3"))
        assertEquals(2, room2.activeUsers.size)
        assertFalse(room2.activeUsers.contains("User2"))
        assertFalse(room2.activeUsers.contains("User3"))

        // Retain only specific elements
        serverRoom.activeUsers.retainAll(setOf("Admin"))
        assertEquals(1, room1.activeUsers.size)
        assertTrue(room1.activeUsers.contains("Admin"))
        assertFalse(room1.activeUsers.contains("User4"))
        assertEquals(1, room2.activeUsers.size)
        assertTrue(room2.activeUsers.contains("Admin"))
        assertFalse(room2.activeUsers.contains("User4"))

        // Clear the set
        serverRoom.activeUsers.clear()
        assertEquals(0, room1.activeUsers.size)
        assertTrue(room1.activeUsers.isEmpty())
        assertEquals(0, room2.activeUsers.size)
        assertTrue(room2.activeUsers.isEmpty())
    }
}
