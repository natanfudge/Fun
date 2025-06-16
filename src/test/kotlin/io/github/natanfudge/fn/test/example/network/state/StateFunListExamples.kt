//package io.github.natanfudge.fn.test.example.network.state
//
//import io.github.natanfudge.fn.network.Fun
//import io.github.natanfudge.fn.network.FunStateContext
//import io.github.natanfudge.fn.network.LocalMultiplayer
//import io.github.natanfudge.fn.network.state.funList
//import org.junit.jupiter.api.Test
//import kotlin.test.assertEquals
//import kotlin.test.assertTrue
//
//class StateFunListExamples {
//    @Test
//    fun funListExample() {
//        // Create a class that extends Fun and has a synchronized list
//        class ChatRoom(id: String, client: FunStateContext) : Fun(id, client) {
//            val messages = funList<String>("messages", "Welcome to the chat!")
//        }
//
//        // Set up a multiplayer environment with two clients
//        val multiplayer = LocalMultiplayer(2)
//        val chatRoom1 = ChatRoom("chat-room", multiplayer.clients[0])
//        val chatRoom2 = ChatRoom("chat-room", multiplayer.clients[1]) // Same ID to sync state
//        val serverChatRoom = ChatRoom("chat-room", multiplayer.server) // Server instance for state changes
//
//        // Verify initial values are synchronized
//        assertEquals(1, chatRoom2.messages.size)
//        assertEquals("Welcome to the chat!", chatRoom2.messages[0])
//
//        // Add items to the list on server and verify they sync to clients
//        serverChatRoom.messages.add("Hello from server")
//
//        assertEquals(2, chatRoom1.messages.size)
//        assertEquals("Hello from server", chatRoom1.messages[1])
//        assertEquals(2, chatRoom2.messages.size)
//        assertEquals("Hello from server", chatRoom2.messages[1])
//
//        // Modify the list on server and verify changes sync to clients
//        serverChatRoom.messages.add("Another message from server")
//        serverChatRoom.messages[0] = "Updated welcome message"
//
//        assertEquals(3, chatRoom1.messages.size)
//        assertEquals("Updated welcome message", chatRoom1.messages[0])
//        assertEquals("Another message from server", chatRoom1.messages[2])
//        assertEquals(3, chatRoom2.messages.size)
//        assertEquals("Updated welcome message", chatRoom2.messages[0])
//        assertEquals("Another message from server", chatRoom2.messages[2])
//
//        // Demonstrate other list operations
//        serverChatRoom.messages.removeAt(1) // Remove "Hello from server"
//        assertEquals(2, chatRoom1.messages.size)
//        assertEquals("Updated welcome message", chatRoom1.messages[0])
//        assertEquals("Another message from server", chatRoom1.messages[1])
//        assertEquals(2, chatRoom2.messages.size)
//        assertEquals("Updated welcome message", chatRoom2.messages[0])
//        assertEquals("Another message from server", chatRoom2.messages[1])
//
//        // Add multiple items at once
//        serverChatRoom.messages.addAll(listOf("Message 3", "Message 4"))
//        assertEquals(4, chatRoom1.messages.size)
//        assertEquals("Message 3", chatRoom1.messages[2])
//        assertEquals("Message 4", chatRoom1.messages[3])
//        assertEquals(4, chatRoom2.messages.size)
//        assertEquals("Message 3", chatRoom2.messages[2])
//        assertEquals("Message 4", chatRoom2.messages[3])
//
//        // Clear the list
//        serverChatRoom.messages.clear()
//        assertEquals(0, chatRoom1.messages.size)
//        assertTrue(chatRoom1.messages.isEmpty())
//        assertEquals(0, chatRoom2.messages.size)
//        assertTrue(chatRoom2.messages.isEmpty())
//    }
//}
