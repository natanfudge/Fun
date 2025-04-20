package io.github.natanfudge.fn.test

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunClient
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.funList
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test

/**
 * Tests for the funList function in the Fun networking system.
 */
class FunListTest {
    /**
     * Tests that a funList properly synchronizes list operations between multiple clients.
     */
    @Test
    fun basicFunListSynchronization() {
        // Create a local multiplayer environment with 2 clients
        val multiplayer = LocalMultiplayer(2)

        // Create a game inventory for client 1
        val client1Inventory = GameInventory("test-inventory", multiplayer.clients[0])

        // Create a game inventory for client 2 with the same ID
        val client2Inventory = GameInventory("test-inventory", multiplayer.clients[1])

        // Verify initial state is synchronized
        assertEquals(0, client1Inventory.items.size, "Initial items size via client1")
        assertEquals(0, client2Inventory.items.size, "Initial items size via client2")

        // Client 1 adds an item
        client1Inventory.items.add("Sword")

        // Verify item is synchronized to client 2
        assertEquals(1, client1Inventory.items.size, "Items size after add via client1")
        assertEquals(1, client2Inventory.items.size, "Items size after add via client2")
        assertEquals("Sword", client1Inventory.items[0], "Item content via client1")
        assertEquals("Sword", client2Inventory.items[0], "Item content via client2")

        // Client 2 adds another item
        client2Inventory.items.add("Shield")

        // Verify both items are synchronized
        assertEquals(2, client1Inventory.items.size, "Items size after second add via client1")
        assertEquals(2, client2Inventory.items.size, "Items size after second add via client2")
        assertEquals("Shield", client1Inventory.items[1], "Second item content via client1")
        assertEquals("Shield", client2Inventory.items[1], "Second item content via client2")
    }

    /**
     * Tests that all list operations are properly synchronized between clients.
     */
    @Test
    fun comprehensiveFunListOperations() {
        // Create a local multiplayer environment with 2 clients
        val multiplayer = LocalMultiplayer(2)

        // Create a game inventory for each client with the same ID
        val client1Inventory = GameInventory("test-inventory", multiplayer.clients[0])
        val client2Inventory = GameInventory("test-inventory", multiplayer.clients[1])

        // Test addAll operation
        client1Inventory.items.addAll(listOf("Sword", "Shield", "Potion"))
        assertEquals(3, client1Inventory.items.size, "Items size after addAll via client1")
        assertEquals(3, client2Inventory.items.size, "Items size after addAll via client2")
        assertEquals("Sword", client1Inventory.items[0], "First item after addAll via client1")
        assertEquals("Shield", client1Inventory.items[1], "Second item after addAll via client1")
        assertEquals("Potion", client1Inventory.items[2], "Third item after addAll via client1")
        assertEquals("Sword", client2Inventory.items[0], "First item after addAll via client2")
        assertEquals("Shield", client2Inventory.items[1], "Second item after addAll via client2")
        assertEquals("Potion", client2Inventory.items[2], "Third item after addAll via client2")

//         Test set operation
        client2Inventory.items[1] = "Large Shield"
        assertEquals("Large Shield", client1Inventory.items[1], "Item after set via client1")
        assertEquals("Large Shield", client2Inventory.items[1], "Item after set via client2")

//         Test remove operation
        val removeResult = client1Inventory.items.remove("Potion")
        assertTrue(removeResult, "Remove operation should return true")
        assertEquals(2, client1Inventory.items.size, "Items size after remove via client1")
        assertEquals(2, client2Inventory.items.size, "Items size after remove via client2")
        assertEquals("Sword", client1Inventory.items[0], "First item after remove via client1")
        assertEquals("Large Shield", client1Inventory.items[1], "Second item after remove via client1")
        assertEquals("Sword", client2Inventory.items[0], "First item after remove via client2")
        assertEquals("Large Shield", client2Inventory.items[1], "Second item after remove via client2")

//         Test removeAt operation
        val removedItem = client2Inventory.items.removeAt(0)
        assertEquals("Sword", removedItem, "Removed item should be 'Sword'")
        assertEquals(1, client1Inventory.items.size, "Items size after removeAt via client1")
        assertEquals(1, client2Inventory.items.size, "Items size after removeAt via client2")
        assertEquals("Large Shield", client1Inventory.items[0], "Item after removeAt via client1")
        assertEquals("Large Shield", client2Inventory.items[0], "Item after removeAt via client2")

        // Test add at index operation
        client1Inventory.items.add(0, "Helmet")
        assertEquals(2, client1Inventory.items.size, "Items size after indexed add via client1")
        assertEquals(2, client2Inventory.items.size, "Items size after indexed add via client2")
        assertEquals("Helmet", client1Inventory.items[0], "First item after indexed add via client1")
        assertEquals("Large Shield", client1Inventory.items[1], "Second item after indexed add via client1")
        assertEquals("Helmet", client2Inventory.items[0], "First item after indexed add via client2")
        assertEquals("Large Shield", client2Inventory.items[1], "Second item after indexed add via client2")

        // Test addAll at index operation
        client2Inventory.items.addAll(1, listOf("Gloves", "Boots"))
        assertEquals(4, client1Inventory.items.size, "Items size after indexed addAll via client1")
        assertEquals(4, client2Inventory.items.size, "Items size after indexed addAll via client2")
        assertEquals("Helmet", client1Inventory.items[0], "First item after indexed addAll via client1")
        assertEquals("Gloves", client1Inventory.items[1], "Second item after indexed addAll via client1")
        assertEquals("Boots", client1Inventory.items[2], "Third item after indexed addAll via client1")
        assertEquals("Large Shield", client1Inventory.items[3], "Fourth item after indexed addAll via client1")
        assertEquals("Helmet", client2Inventory.items[0], "First item after indexed addAll via client2")
        assertEquals("Gloves", client2Inventory.items[1], "Second item after indexed addAll via client2")
        assertEquals("Boots", client2Inventory.items[2], "Third item after indexed addAll via client2")
        assertEquals("Large Shield", client2Inventory.items[3], "Fourth item after indexed addAll via client2")

        // Test removeAll operation
        val removeAllResult = client1Inventory.items.removeAll(listOf("Gloves", "Boots"))
        assertTrue(removeAllResult, "RemoveAll operation should return true")
        assertEquals(2, client1Inventory.items.size, "Items size after removeAll via client1")
        assertEquals(2, client2Inventory.items.size, "Items size after removeAll via client2")
        assertEquals("Helmet", client1Inventory.items[0], "First item after removeAll via client1")
        assertEquals("Large Shield", client1Inventory.items[1], "Second item after removeAll via client1")
        assertEquals("Helmet", client2Inventory.items[0], "First item after removeAll via client2")
        assertEquals("Large Shield", client2Inventory.items[1], "Second item after removeAll via client2")

        // Test retainAll operation
        val retainAllResult = client2Inventory.items.retainAll(listOf("Helmet"))
        assertTrue(retainAllResult, "RetainAll operation should return true")
        assertEquals(1, client1Inventory.items.size, "Items size after retainAll via client1")
        assertEquals(1, client2Inventory.items.size, "Items size after retainAll via client2")
        assertEquals("Helmet", client1Inventory.items[0], "Item after retainAll via client1")
        assertEquals("Helmet", client2Inventory.items[0], "Item after retainAll via client2")

        // Test clear operation
        client1Inventory.items.clear()
        assertEquals(0, client1Inventory.items.size, "Items size after clear via client1")
        assertEquals(0, client2Inventory.items.size, "Items size after clear via client2")
        assertTrue(client1Inventory.items.isEmpty(), "Items should be empty after clear via client1")
        assertTrue(client2Inventory.items.isEmpty(), "Items should be empty after clear via client2")
    }

    /**
     * Tests the vararg constructor of funList.
     */
    @Test
    fun funListVarargConstructor() {
        // Create a local multiplayer environment with 2 clients
        val multiplayer = LocalMultiplayer(2)

        // Create a game inventory with initial items for each client
        val client1Inventory = GameInventoryWithInitialItems("test-inventory", multiplayer.clients[0])
        val client2Inventory = GameInventoryWithInitialItems("test-inventory", multiplayer.clients[1])

        // Verify initial state is synchronized
        assertEquals(3, client1Inventory.initialItems.size, "Initial items size via client1")
        assertEquals(3, client2Inventory.initialItems.size, "Initial items size via client2")
        assertEquals("Sword", client1Inventory.initialItems[0], "First initial item via client1")
        assertEquals("Shield", client1Inventory.initialItems[1], "Second initial item via client1")
        assertEquals("Potion", client1Inventory.initialItems[2], "Third initial item via client1")
        assertEquals("Sword", client2Inventory.initialItems[0], "First initial item via client2")
        assertEquals("Shield", client2Inventory.initialItems[1], "Second initial item via client2")
        assertEquals("Potion", client2Inventory.initialItems[2], "Third initial item via client2")

        // Client 1 modifies the list
        client1Inventory.initialItems.remove("Shield")

        // Verify change is synchronized
        assertEquals(2, client1Inventory.initialItems.size, "Items size after remove via client1")
        assertEquals(2, client2Inventory.initialItems.size, "Items size after remove via client2")
        assertEquals("Sword", client1Inventory.initialItems[0], "First item after remove via client1")
        assertEquals("Potion", client1Inventory.initialItems[1], "Second item after remove via client1")
        assertEquals("Sword", client2Inventory.initialItems[0], "First item after remove via client2")
        assertEquals("Potion", client2Inventory.initialItems[1], "Second item after remove via client2")
    }
}

/**
 * A game inventory that uses funList to synchronize items between clients.
 */
class GameInventory(
    id: String,
    client: FunClient
) : Fun(id, client) {
    // List of items in the inventory
    val items = funList<String>("items", serializer(), mutableListOf())
}

/**
 * A game inventory that uses the vararg constructor of funList.
 */
class GameInventoryWithInitialItems(
    id: String,
    client: FunClient
) : Fun(id, client) {
    // List of initial items in the inventory
    val initialItems = funList("initialItems", "Sword", "Shield", "Potion")
}
