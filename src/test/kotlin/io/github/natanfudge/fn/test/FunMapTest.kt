package io.github.natanfudge.fn.test

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunStateManager
import io.github.natanfudge.fn.network.LocalMultiplayer
import io.github.natanfudge.fn.network.state.funMap
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test

/**
 * Tests for the funMap function in the Fun networking system.
 */
class FunMapTest {
    /**
     * Tests that a funMap properly synchronizes map operations between multiple clients.
     */
    @Test
    fun basicFunMapSynchronization() {
        // Create a local multiplayer environment with 2 clients
        val multiplayer = LocalMultiplayer(2)

        // Create a game inventory for client 1
        val client1Inventory = GameItemProperties("test-inventory", multiplayer.clients[0])

        // Create a game inventory for client 2 with the same ID
        val client2Inventory = GameItemProperties("test-inventory", multiplayer.clients[1])

        // Verify initial state is synchronized
        assertEquals(0, client1Inventory.properties.size, "Initial properties size via client1")
        assertEquals(0, client2Inventory.properties.size, "Initial properties size via client2")

        // Client 1 adds an item property
        client1Inventory.properties["Sword"] = 10

        // Verify property is synchronized to client 2
        assertEquals(1, client1Inventory.properties.size, "Properties size after put via client1")
        assertEquals(1, client2Inventory.properties.size, "Properties size after put via client2")
        assertEquals(10, client1Inventory.properties["Sword"], "Property value via client1")
        assertEquals(10, client2Inventory.properties["Sword"], "Property value via client2")

        // Client 2 adds another property
        client2Inventory.properties["Shield"] = 20

        // Verify both properties are synchronized
        assertEquals(2, client1Inventory.properties.size, "Properties size after second put via client1")
        assertEquals(2, client2Inventory.properties.size, "Properties size after second put via client2")
        assertEquals(20, client1Inventory.properties["Shield"], "Second property value via client1")
        assertEquals(20, client2Inventory.properties["Shield"], "Second property value via client2")
    }

    /**
     * Tests that all map operations are properly synchronized between clients.
     */
    @Test
    fun comprehensiveFunMapOperations() {
        // Create a local multiplayer environment with 2 clients
        val multiplayer = LocalMultiplayer(2)

        // Create a game inventory for each client with the same ID
        val client1Inventory = GameItemProperties("test-inventory", multiplayer.clients[0])
        val client2Inventory = GameItemProperties("test-inventory", multiplayer.clients[1])

        // Test put operation
        client1Inventory.properties["Sword"] = 10
        client1Inventory.properties["Shield"] = 20
        client1Inventory.properties["Potion"] = 5
        assertEquals(3, client1Inventory.properties.size, "Properties size after puts via client1")
        assertEquals(3, client2Inventory.properties.size, "Properties size after puts via client2")
        assertEquals(10, client1Inventory.properties["Sword"], "First property after puts via client1")
        assertEquals(20, client1Inventory.properties["Shield"], "Second property after puts via client1")
        assertEquals(5, client1Inventory.properties["Potion"], "Third property after puts via client1")
        assertEquals(10, client2Inventory.properties["Sword"], "First property after puts via client2")
        assertEquals(20, client2Inventory.properties["Shield"], "Second property after puts via client2")
        assertEquals(5, client2Inventory.properties["Potion"], "Third property after puts via client2")

        // Test update operation
        client2Inventory.properties["Shield"] = 25
        assertEquals(25, client1Inventory.properties["Shield"], "Property after update via client1")
        assertEquals(25, client2Inventory.properties["Shield"], "Property after update via client2")

        // Test remove operation
        val removedValue = client1Inventory.properties.remove("Potion")
        assertEquals(5, removedValue, "Removed value should be 5")
        assertEquals(2, client1Inventory.properties.size, "Properties size after remove via client1")
        assertEquals(2, client2Inventory.properties.size, "Properties size after remove via client2")
        assertNull(client1Inventory.properties["Potion"], "Removed property should be null via client1")
        assertNull(client2Inventory.properties["Potion"], "Removed property should be null via client2")

        // Test containsKey operation
        assertTrue(client1Inventory.properties.containsKey("Sword"), "Should contain key 'Sword' via client1")
        assertTrue(client2Inventory.properties.containsKey("Sword"), "Should contain key 'Sword' via client2")
        assertFalse(client1Inventory.properties.containsKey("Potion"), "Should not contain key 'Potion' via client1")
        assertFalse(client2Inventory.properties.containsKey("Potion"), "Should not contain key 'Potion' via client2")

        // Test containsValue operation
        assertTrue(client1Inventory.properties.containsValue(10), "Should contain value 10 via client1")
        assertTrue(client2Inventory.properties.containsValue(10), "Should contain value 10 via client2")
        assertFalse(client1Inventory.properties.containsValue(5), "Should not contain value 5 via client1")
        assertFalse(client2Inventory.properties.containsValue(5), "Should not contain value 5 via client2")

        // Test putAll operation
        val additionalItems = mapOf("Helmet" to 15, "Boots" to 12)
        client2Inventory.properties.putAll(additionalItems)
        assertEquals(4, client1Inventory.properties.size, "Properties size after putAll via client1")
        assertEquals(4, client2Inventory.properties.size, "Properties size after putAll via client2")
        assertEquals(15, client1Inventory.properties["Helmet"], "New property 'Helmet' after putAll via client1")
        assertEquals(12, client1Inventory.properties["Boots"], "New property 'Boots' after putAll via client1")
        assertEquals(15, client2Inventory.properties["Helmet"], "New property 'Helmet' after putAll via client2")
        assertEquals(12, client2Inventory.properties["Boots"], "New property 'Boots' after putAll via client2")

        // Test clear operation
        client1Inventory.properties.clear()
        assertEquals(0, client1Inventory.properties.size, "Properties size after clear via client1")
        assertEquals(0, client2Inventory.properties.size, "Properties size after clear via client2")
        assertTrue(client1Inventory.properties.isEmpty(), "Properties should be empty after clear via client1")
        assertTrue(client2Inventory.properties.isEmpty(), "Properties should be empty after clear via client2")
    }

    /**
     * Tests the vararg constructor of funMap.
     */
    @Test
    fun funMapVarargConstructor() {
        // Create a local multiplayer environment with 2 clients
        val multiplayer = LocalMultiplayer(2)

        // Create a game inventory with initial properties for each client
        val client1Inventory = GameItemPropertiesWithInitialItems("test-inventory", multiplayer.clients[0])
        val client2Inventory = GameItemPropertiesWithInitialItems("test-inventory", multiplayer.clients[1])

        // Verify initial state is synchronized
        assertEquals(3, client1Inventory.initialProperties.size, "Initial properties size via client1")
        assertEquals(3, client2Inventory.initialProperties.size, "Initial properties size via client2")
        assertEquals(10, client1Inventory.initialProperties["Sword"], "First initial property via client1")
        assertEquals(20, client1Inventory.initialProperties["Shield"], "Second initial property via client1")
        assertEquals(5, client1Inventory.initialProperties["Potion"], "Third initial property via client1")
        assertEquals(10, client2Inventory.initialProperties["Sword"], "First initial property via client2")
        assertEquals(20, client2Inventory.initialProperties["Shield"], "Second initial property via client2")
        assertEquals(5, client2Inventory.initialProperties["Potion"], "Third initial property via client2")

        // Client 1 modifies the map
        client1Inventory.initialProperties.remove("Shield")

        // Verify change is synchronized
        assertEquals(2, client1Inventory.initialProperties.size, "Properties size after remove via client1")
        assertEquals(2, client2Inventory.initialProperties.size, "Properties size after remove via client2")
        assertEquals(10, client1Inventory.initialProperties["Sword"], "First property after remove via client1")
        assertEquals(5, client1Inventory.initialProperties["Potion"], "Second property after remove via client1")
        assertEquals(10, client2Inventory.initialProperties["Sword"], "First property after remove via client2")
        assertEquals(5, client2Inventory.initialProperties["Potion"], "Second property after remove via client2")
    }
}

/**
 * A game inventory that uses funMap to synchronize item properties between clients.
 */
class GameItemProperties(
    id: String,
    client: FunStateManager
) : Fun(id, client) {
    // Map of item properties in the inventory
    val properties = funMap<String, Int>("properties", serializer(), serializer(), mutableMapOf())
}

/**
 * A game inventory that uses the vararg constructor of funMap.
 */
class GameItemPropertiesWithInitialItems(
    id: String,
    client: FunStateManager
) : Fun(id, client) {
    // Map of initial item properties in the inventory
    val initialProperties = funMap("initialProperties", 
        "Sword" to 10, 
        "Shield" to 20, 
        "Potion" to 5
    )
}
