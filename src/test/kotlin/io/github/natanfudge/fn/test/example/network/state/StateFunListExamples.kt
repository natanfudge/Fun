package io.github.natanfudge.fn.test.example.network.state

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunContext
import io.github.natanfudge.fn.network.FunStateManager
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.state.funList
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Disabled
class StateFunListExamples {
    @Disabled
    @Test
    fun funListExample() {
        // Create a class that extends Fun and has a synchronized list
        class ChatRoom(id: String, client: FunContext) : Fun(id, client) {
            val messages = funList<String>("messages", "Welcome to the chat!")
        }

        // Set up a multiplayer environment with two clients
        val multiplayer = LocalMultiplayer(2)
        val chatRoom1 = ChatRoom("chat-room", multiplayer.clients[0])
        val chatRoom2 = ChatRoom("chat-room", multiplayer.clients[1]) // Same ID to sync state

        // Verify initial values are synchronized
        assertEquals(1, chatRoom2.messages.size)
        assertEquals("Welcome to the chat!", chatRoom2.messages[0])

        // Add items to the list on chatRoom1 and verify they sync to chatRoom2
        chatRoom1.messages.add("Hello from client 1")

        assertEquals(2, chatRoom2.messages.size)
        assertEquals("Hello from client 1", chatRoom2.messages[1])

        // Modify the list on chatRoom2 and verify changes sync back to chatRoom1
        chatRoom2.messages.add("Hello from client 2")
        chatRoom2.messages[0] = "Updated welcome message"

        assertEquals(3, chatRoom1.messages.size)
        assertEquals("Updated welcome message", chatRoom1.messages[0])
        assertEquals("Hello from client 2", chatRoom1.messages[2])

        // Demonstrate other list operations
        chatRoom1.messages.removeAt(1) // Remove "Hello from client 1"
        assertEquals(2, chatRoom2.messages.size)
        assertEquals("Updated welcome message", chatRoom2.messages[0])
        assertEquals("Hello from client 2", chatRoom2.messages[1])

        // Add multiple items at once
        chatRoom2.messages.addAll(listOf("Message 3", "Message 4"))
        assertEquals(4, chatRoom1.messages.size)
        assertEquals("Message 3", chatRoom1.messages[2])
        assertEquals("Message 4", chatRoom1.messages[3])

        // Clear the list
        chatRoom1.messages.clear()
        assertEquals(0, chatRoom2.messages.size)
        assertTrue(chatRoom2.messages.isEmpty())
    }
}
