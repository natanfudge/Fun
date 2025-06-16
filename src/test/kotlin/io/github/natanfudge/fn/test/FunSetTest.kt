//package io.github.natanfudge.fn.test
//
//import io.github.natanfudge.fn.network.Fun
//import io.github.natanfudge.fn.network.FunStateContext
//import io.github.natanfudge.fn.network.LocalMultiplayer
//import io.github.natanfudge.fn.network.state.funSet
//import kotlinx.serialization.serializer
//import org.junit.jupiter.api.Test
//import kotlin.test.assertEquals
//import kotlin.test.assertFalse
//import kotlin.test.assertTrue
//
///**
// * Tests for the funSet function in the Fun networking system.
// */
//class FunSetTest {
//    /**
//     * Tests that a funSet properly synchronizes set operations between multiple clients.
//     */
//    @Test
//    fun basicFunSetSynchronization() {
//        // Create a local multiplayer testing environment
//        val multiplayer = LocalMultiplayer(2)
//
//        // Create two clients for testing
//        val client1 = multiplayer.clients[0]
//        val client2 = multiplayer.clients[1]
//        // Create server for testing
//        val server = multiplayer.server
//
//        // Create inventory for each client with the same ID
//        val client1Inventory = ItemCollection("inventory", client1)
//        val client2Inventory = ItemCollection("inventory", client2)
//        // Create inventory for server with the same ID
//        val serverInventory = ItemCollection("inventory", server)
//
//        // Add items via server and verify they're synced to clients
//        serverInventory.items.add("Helmet")
//        serverInventory.items.add("Chestplate")
//
//        assertEquals(2, client1Inventory.items.size, "Items size after adding via server")
//        assertEquals(2, client2Inventory.items.size, "Items size after adding via server")
//        assertTrue(client1Inventory.items.contains("Helmet"), "Helmet should be in client1's items")
//        assertTrue(client1Inventory.items.contains("Chestplate"), "Chestplate should be in client1's items")
//        assertTrue(client2Inventory.items.contains("Helmet"), "Helmet should be in client2's items")
//        assertTrue(client2Inventory.items.contains("Chestplate"), "Chestplate should be in client2's items")
//
//        // Remove an item via server and verify it's removed from clients
//        val removeResult = serverInventory.items.remove("Helmet")
//        assertTrue(removeResult, "Remove operation should return true")
//        assertEquals(1, client1Inventory.items.size, "Items size after removing via server")
//        assertEquals(1, client2Inventory.items.size, "Items size after removing via server")
//        assertFalse(client1Inventory.items.contains("Helmet"), "Helmet should not be in client1's items")
//        assertTrue(client1Inventory.items.contains("Chestplate"), "Chestplate should still be in client1's items")
//        assertFalse(client2Inventory.items.contains("Helmet"), "Helmet should not be in client2's items")
//        assertTrue(client2Inventory.items.contains("Chestplate"), "Chestplate should still be in client2's items")
//    }
//
//    /**
//     * Tests that all set operations are properly synchronized between clients.
//     */
//    @Test
//    fun comprehensiveFunSetOperations() {
//        // Setup local multiplayer environment
//        val multiplayer = LocalMultiplayer(2)
//        val client1 = multiplayer.clients[0]
//        val client2 = multiplayer.clients[1]
//        val server = multiplayer.server
//
//        val client1Inventory = ItemCollection("inventory", client1)
//        val client2Inventory = ItemCollection("inventory", client2)
//        val serverInventory = ItemCollection("inventory", server)
//
//        // Test adding multiple items with addAll
//        val itemsToAdd = setOf("Helmet", "Chestplate", "Boots")
//        val addAllResult = serverInventory.items.addAll(itemsToAdd)
//
//        assertTrue(addAllResult, "AddAll operation should return true")
//        assertEquals(3, client1Inventory.items.size, "Items size after addAll via server")
//        assertEquals(3, client2Inventory.items.size, "Items size after addAll via server")
//        assertTrue(client1Inventory.items.containsAll(itemsToAdd), "All items should be in client1's items")
//        assertTrue(client2Inventory.items.containsAll(itemsToAdd), "All items should be in client2's items")
//
//        // Test removing multiple items with removeAll
//        val itemsToRemove = setOf("Chestplate", "NonexistentItem")
//        val removeAllResult = serverInventory.items.removeAll(itemsToRemove)
//
//        assertTrue(removeAllResult, "RemoveAll operation should return true")
//        assertEquals(2, client1Inventory.items.size, "Items size after removeAll via server")
//        assertEquals(2, client2Inventory.items.size, "Items size after removeAll via server")
//        assertFalse(client1Inventory.items.contains("Chestplate"), "Chestplate should not be in client1's items")
//        assertTrue(client1Inventory.items.contains("Helmet"), "Helmet should still be in client1's items")
//        assertTrue(client1Inventory.items.contains("Boots"), "Boots should still be in client1's items")
//        assertFalse(client2Inventory.items.contains("Chestplate"), "Chestplate should not be in client2's items")
//        assertTrue(client2Inventory.items.contains("Helmet"), "Helmet should still be in client2's items")
//        assertTrue(client2Inventory.items.contains("Boots"), "Boots should still be in client2's items")
//
//        // Test retainAll operation
//        val retainAllResult = serverInventory.items.retainAll(listOf("Helmet"))
//        assertTrue(retainAllResult, "RetainAll operation should return true")
//        assertEquals(1, client1Inventory.items.size, "Items size after retainAll via server")
//        assertEquals(1, client2Inventory.items.size, "Items size after retainAll via server")
//        assertTrue(client1Inventory.items.contains("Helmet"), "Helmet should be in client1's items after retainAll")
//        assertFalse(client1Inventory.items.contains("Boots"), "Boots should not be in client1's items after retainAll")
//        assertTrue(client2Inventory.items.contains("Helmet"), "Helmet should be in client2's items after retainAll")
//        assertFalse(client2Inventory.items.contains("Boots"), "Boots should not be in client2's items after retainAll")
//
//        // Test clear operation
//        serverInventory.items.clear()
//        assertEquals(0, client1Inventory.items.size, "Items size after clear via server")
//        assertEquals(0, client2Inventory.items.size, "Items size after clear via server")
//        assertTrue(client1Inventory.items.isEmpty(), "Items should be empty after clear via server")
//        assertTrue(client2Inventory.items.isEmpty(), "Items should be empty after clear via server")
//    }
//
//    /**
//     * Tests the vararg constructor of funSet.
//     */
//    @Test
//    fun funSetVarargConstructor() {
//        val multiplayer = LocalMultiplayer(2)
//        val client1 = multiplayer.clients[0]
//        val client2 = multiplayer.clients[1]
//        val server = multiplayer.server
//
//        val client1Inventory = ItemCollectionWithInitialItems("inventory", client1)
//        val client2Inventory = ItemCollectionWithInitialItems("inventory", client2)
//        val serverInventory = ItemCollectionWithInitialItems("inventory", server)
//
//        // Verify initial items are synchronized
//        assertEquals(3, client1Inventory.initialItems.size, "Initial items size for client1")
//        assertEquals(3, client2Inventory.initialItems.size, "Initial items size for client2")
//        assertTrue(client1Inventory.initialItems.contains("Sword"), "Sword should be in client1's initial items")
//        assertTrue(client1Inventory.initialItems.contains("Shield"), "Shield should be in client1's initial items")
//        assertTrue(client1Inventory.initialItems.contains("Potion"), "Potion should be in client1's initial items")
//        assertTrue(client2Inventory.initialItems.contains("Sword"), "Sword should be in client2's initial items")
//        assertTrue(client2Inventory.initialItems.contains("Shield"), "Shield should be in client2's initial items")
//        assertTrue(client2Inventory.initialItems.contains("Potion"), "Potion should be in client2's initial items")
//
//        // Modify the set to ensure updates still work
//        serverInventory.initialItems.add("Bow")
//        assertEquals(4, client1Inventory.initialItems.size, "Items size after adding via server")
//        assertEquals(4, client2Inventory.initialItems.size, "Items size after adding via server")
//        assertTrue(client1Inventory.initialItems.contains("Bow"), "Bow should be in client1's items")
//        assertTrue(client2Inventory.initialItems.contains("Bow"), "Bow should be in client2's items")
//    }
//}
//
///**
// * A collection of items that uses funSet to synchronize between clients.
// */
//class ItemCollection(
//    id: String,
//    client: FunStateContext
//) : Fun(id, client) {
//    // Set of items in the collection
//    val items = funSet<String>("items", serializer(), mutableSetOf())
//}
//
///**
// * A collection of items that uses the vararg constructor of funSet.
// */
//class ItemCollectionWithInitialItems(
//    id: String,
//    client: FunStateContext
//) : Fun(id, client) {
//    // Set of initial items in the collection
//    val initialItems = funSet("initialItems", "Sword", "Shield", "Potion")
//}
