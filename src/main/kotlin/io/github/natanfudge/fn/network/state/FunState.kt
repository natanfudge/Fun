@file:Suppress("UNCHECKED_CAST", "FunctionName", "PropertyName")

package io.github.natanfudge.fn.network.state

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json


//TODO: things to think about:
// 2. 'secret' values - values only visible to their owner.
// 3. Protection of values - modifying values only from the server. Permission system - usually all permissions given to the server
// 4. Prediction - running server logic on the client for as long as possible
// 4b. Prediction + server correction/rubberbanding. How do we allow prediction while avoiding incorrect values?
// 5b. prediction + server messages - how do we avoid server messages causing redundant or incorrectly ordered state changes - such as when a
// list add is predicted and the server also applies the add, resulting in 2 adds instead of the intended single add?
// 5. Merged client-server optimization - how can we reuse objects in case the client and server are running in the same process?
// 6. See how we can optimize object IDs in production to avoid a separate ID for each instance
// 7. Some sort of API Fun.child(id: String) that creates a child state of a Fun.
// 8. We could have a SinglePlayerFun that doesn't require specifying a client.
// 9. Compiler plugin: see compiler plugin.md
// 10. Think about how we are gonna pass Fun components through RPC methods

//TODO: things to implement:
// 1. Make sure messages arrive at the order they were sent (see multithreading doc in FunState)

/**
 * Represents a serialized value that can be sent over the network.
 */
internal typealias NetworkValue = String
// SLOW: we can avoid serialization in case both clients are in the same process.
// Maybe we just type NetworkValue as Any and then we can use the values themselves as the NetworkValues
internal fun <T> NetworkValue.decode(serializer: KSerializer<T>): T = Json.decodeFromString(serializer, this)
internal fun <T> T.toNetwork(serializer: KSerializer<T>) = Json.encodeToString(serializer, this)



/**
 * Default implementation of [FunStateHolder] that stores state in a map.
 */
internal class MapStateHolder : FunStateHolder {
    // Values that were sent to an object but the object did not have a chance to react to them yet,
    // because he did not try getting/setting the value yet.
    // This is mostly because of the limitation that we only get the key information from
    // ReadWriteProperty#getValue / setValue, and only at that point we can start registering the state holders.
    private val pendingValues = mutableMapOf<String, StateChange.SetProperty>()

    private val map = mutableMapOf<String, FunState>()

    /**
     * Updates the value of a property identified by [key].
     * If the property hasn't been registered yet, the value is stored as pending.
     */
    override fun applyChange(key: String, change: StateChange) {
        if (key in map) {
            val state = map.getValue(key)
            state.applyChange(change)
        } else {
            check(change is StateChange.SetProperty) { "The list with the key $key was unexpectedly not registered prior to having a change applied to it" }
            // Not registered yet, wait for it to be registered to grant it the value
            pendingValues[key] = change
        }
    }


    /**
     * Registers a state property with this holder.
     */
    fun registerState(key: String, value: FunState) {
        map[key] = value
    }

    /**
     * Sets a pending value to a state property if one exists.
     */
    fun <T> setPendingValue(key: String, state: FunValue<T>) {
        if (key !in pendingValues) return
        val networkValue = pendingValues.getValue(key)
        state.applyChange(networkValue)
        pendingValues.remove(key)
    }
}


/**
 * Interface for objects that can hold and update multiple state properties.
 * 
 * A FunStateHolder manages a collection of [FunState] objects, each identified by a unique key.
 * It's responsible for routing incoming state changes to the appropriate state object.
 * 
 * The primary implementation is [MapStateHolder], which stores state objects in a map.
 * 
 * @see FunState
 * @see MapStateHolder
 */
interface FunStateHolder {
    /**
     * Updates the value of a property identified by [key].
     * 
     * When a state change is received from the network, this method routes it to the
     * appropriate [FunState] object based on the key.
     * 
     * [key] The unique identifier for the state property
     * [change] The state change to apply
     */
    fun applyChange(key: String, change: StateChange)
}


/**
 * Base interface for all synchronized state objects in the Fun network system.
 * 
 * FunState objects represent data that is automatically synchronized across all clients
 * in a multiplayer environment. When a FunState object is modified on one client,
 * the changes are automatically propagated to all other clients.
 * 
 * Implementations include:
 * - [FunValue] - For single values
 * - [FunList] - For synchronized lists
 * - [FunMap] - For synchronized maps
 * - [FunSet] - For synchronized sets
 *
 * *******
 * **Multithreading and FunState**
 *
 * There's four potential points of multithreading danger when using [FunState]s.
 * 1. *One thread modifies a value locally, another modifies it at the same time, boom*.
 *
 *    Here, the challenge is identical to when dealing with normal variables. Multithreaded access
 *    to mutable variables should be guarded with synchronization.
 *
 * 2. *Two state modifications are received externally from the same authority,
 * but with an incorrect order, producing the wrong final state*
 *
 *      This case is solved by Fun itself, by making sure messages arrive at the order they were sent.
 *      As long as the sender observes correct state, the receiver will also observe correct state.
 * 3. *Two different authorities decide on incompatible state changes at the same time*.
 *
 *      It is possible to synchronize such cases on the server
 *      In general, usages for directly setting a value 'on the client's whim', and outside a server+client-prediction scope, is limited,
 *      therefore we require all changes to state be made inside such a context.
 *
 * 4. *A prediction interferes with the message received from the server*
 *
 *     This is a difficult case and is handled extensively by the prediction implementation.
 *
 * *******
 *
 * 
 * @see FunValue
 * @see FunList
 * @see FunMap
 * @see FunSet
 */
interface FunState {
    /**
     * Applies a state change received from the network to this object.
     * 
     * This method is called internally by the Fun framework when a state change
     * is received from another client. Implementations should update their internal
     * state based on the received change.
     */
    @InternalFunApi
    fun applyChange(change: StateChange)
}



// API: enable this in the future
//@RequiresOptIn(
//    level = RequiresOptIn.Level.WARNING,
//    message = "This API is only intended to be used by generated code. You should never access this directly."
//)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
@Retention(AnnotationRetention.BINARY)
annotation class InternalFunApi




/**
 * Represents a change to a state object that can be sent over the network.
 * 
 * StateChange is the core mechanism for synchronizing state between clients.
 * When a state object is modified, a StateChange is created and sent to all clients.
 * Each client then applies the change to its local copy of the state object.
 * 
 * There are different types of state changes for different types of state objects:
 * - Property changes (SetProperty)
 * - List operations (ListOp)
 * - Map operations (MapOp)
 * - Set operations (SetOp)
 * 
 * @see FunState
 */
sealed interface StateChange {
    /**
     * A state change that involves a single value.
     */
    sealed interface SingleChange : StateChange {
        val value: NetworkValue
    }

    /**
     * A change that sets a property to a new value.
     */
    data class SetProperty(override val value: NetworkValue) : SingleChange

    /**
     * A state change that involves multiple values.
     */
    sealed interface BulkChange : StateChange {
        val values: NetworkValue
    }

    /**
     * Marker interface for operations on a [FunList].
     */
    sealed interface ListOp: StateChange

    /**
     * Sets the element at the specified [index] in a list.
     */
    data class ListSet(override val value: NetworkValue, val index: Int) : SingleChange, ListOp, SetOp

    /**
     * Adds an element at the specified [index] in a list.
     */
    data class ListIndexedAdd(override val value: NetworkValue, val index: Int) : SingleChange, ListOp

    /**
     * Removes the element at the specified [index] from a list.
     */
    data class ListRemoveAt(val index: Int) : ListOp

    /**
     * Adds all elements from a collection at the specified [index] in a list.
     */
    data class ListIndexedAddAll(override val values: NetworkValue, val index: Int) : BulkChange, ListOp

    /**
     * Marker interface for operations on a [FunMap].
     */
    sealed interface MapOp: StateChange

    /**
     * Puts a key-value pair in a map.
     */
    data class MapPut(val key: NetworkValue, val value: NetworkValue) : MapOp

    /**
     * Removes a key-value pair from a map.
     */
    data class MapRemove(val key: NetworkValue) : MapOp

    /**
     * Puts all entries from another map into a map.
     */
    data class MapPutAll(val entries: NetworkValue) : MapOp

    /**
     * Marker interface for operations on a [FunSet].
     */
    sealed interface SetOp: StateChange

    /**
     * Clears all elements from a collection.
     */
    object CollectionClear : StateChange, ListOp, MapOp, SetOp

    /**
     * Adds an element to a collection.
     */
    data class CollectionAdd(override val value: NetworkValue) : SingleChange, ListOp, SetOp

    /**
     * Removes an element from a collection.
     */
    data class CollectionRemove(override val value: NetworkValue) : SingleChange, ListOp, SetOp

    /**
     * Removes all elements in the specified collection from this collection.
     */
    data class CollectionRemoveAll(override val values: NetworkValue) : BulkChange, ListOp, SetOp

    /**
     * Retains only the elements in this collection that are contained in the specified collection.
     */
    data class CollectionRetainAll(override val values: NetworkValue) : BulkChange, ListOp, SetOp

    /**
     * Adds all elements from the specified collection to this collection.
     */
    data class CollectionAddAll(override val values: NetworkValue) : BulkChange, ListOp, SetOp
}

internal fun warnMismatchingStateChange(change: StateChange, expected: String) {
    println(
        "Warning - Mismatching message sender and receiver - sender attempted ${change::class} " +
                "but receiver (this client) holds $expected. The message will be ignored (change = ${change})."
    )
}
