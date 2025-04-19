@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.natanfudge.fn.error.UnfunStateException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


//

class World(id: String, client: FunClient) : Fun(id, client) {
    //SLOW: I think right now, it's best to keep the by funState() slow api, and in the future transform it into a normal property
    // and we'll inject stuff into the get() set() of the property to get what we want.
    var width by funState(100)
    var height by mutableStateOf(1000)
}

class Player {
    val inventory = Inventory()
}

class Inventory {
    val swords = mutableStateListOf<Sword>()

}

class Sword {
    val enchantment = Enchantment()
}

class Enchantment {
    var strength by mutableStateOf(10)
    var active by mutableStateOf(false)
}


//TODO: things to think about:
// 1. Multithreading of state manager
// 2. 'secret' values - values only visible to their owner.
// 3. Protection of values - modifying values only from the server. Permission system - usually all permissions given to the server
// 4. Prediction - running server logic on the client for as long as possible
// 5. Merged client-server optimization - how can we reuse objects in case the client and server are running in the same process?
// 6. See how we can optimize object IDs in production to avoid a separate ID for each instance
// 7. Some sort of API Fun.child(id: String) that creates a child state of a Fun.
// 8. We could have a SinglePlayerFun that doesn't require specifying a client.
// 9. Compiler plugin: see compiler plugin.md
// 10. Think about how we are gonna pass Fun components through RPC methods


interface FunCommunication {
    fun <T> send(holderKey: String, propertyKey: String, value: T, serializer: KSerializer<T>)
}

typealias NetworkValue = String


// API: should prob be private constructor
class FunClient(val communication: FunCommunication) {

    private val stateHolders = mutableMapOf<String, MapStateHolder>()
    internal fun receiveUpdate(holderKey: String, propertyKey: String, value: NetworkValue) {
        val holder = stateHolders[holderKey]
        if (holder != null) {
            holder.setValue(propertyKey, value)
        } else {
            println("WARNING: Received a value to the Fun component '${holderKey}', but no such ID exists, so the value was discarded. (value = $value)")
        }
    }

    internal fun <T> sendUpdate(holderKey: String, propertyKey: String, value: T, serializer: KSerializer<T>) {
        // SLOW: we can avoid serialization in case both clients are in the same process
        communication.send(holderKey, propertyKey, value, serializer)
    }

    internal fun register(fn: Fun, state: MapStateHolder) {
        if (fn.id in stateHolders) {
            throw IllegalArgumentException("A state holder with the id '${fn.id}' was registered twice. Make sure to give Fun components unique IDs. ")
        }
        stateHolders[fn.id] = state
    }

    /**
     * This will set the value of [state] to the pending value if it exists.
     * A pending value will get DELETED once it is retrieved!
     */
    internal fun <T> setPendingValue(holderKey: String, propertyKey: String, state: FunState<T>){
        val holder = stateHolders[holderKey] ?: throw UnfunStateException(
            "State holder '$holderKey' was not registered prior to attempting getting the pending value of its sub-state '$propertyKey'!"
        )
         holder.setPendingValue(propertyKey, state)
    }

    internal fun registerState(holderKey: String, propertyKey: String, state: FunState<Any?>) {
        val holder = stateHolders[holderKey] ?: throw UnfunStateException(
            "State holder '$holderKey' was not registered prior to registering its sub-state '$propertyKey'!"
        )
        holder.registerState(propertyKey, state)
    }
}


/**
 * Stores a map entry for each individual property.
 * Easier to use, less performant
 */
class MapStateHolder : FunStateHolder {
    // Values that were sent to an object but the object did not have a chance to react to them yet,
    // because he did not try getting/setting the value yet.
    // This is mostly because of the limitation that we only get the key information from
    // ReadWriteProperty#getValue / setValue, and only at that point we can start registering the state holders.
    private val pendingValues = mutableMapOf<String, NetworkValue>()

    private val map = mutableMapOf<String, FunState<Any?>>()
    override fun setValue(key: String, value: NetworkValue) {
        if (key in map) {
            // Property was properly registered, update it
            map.getValue(key).receiveUpdate(value)
        } else {
            // Not registered yet, wait for it to be registered to grant it the value
            pendingValues[key] = value
        }
    }

    fun registerState(key: String, value: FunState<Any?>) {
        map[key] = value
    }

    fun <T> setPendingValue(key: String, state: FunState<T>) {
        if (key !in pendingValues) return
        val networkValue = pendingValues.getValue(key)
        state.receiveUpdate(networkValue)
        pendingValues.remove(key)
    }
}


/**
 * If you implement it manually it could be more performant than [MapStateHolder]
 */
interface FunStateHolder {
    fun setValue(key: String, value: NetworkValue)
}

abstract class Fun(
     val id: String,
    val client: FunClient,
){
    init {
        client.register(this, MapStateHolder())
    }
}



/**
 * Is only usable inside a class extending [SomeFun].
 */
inline fun <reified T> funState(value: T): FunState<T> = FunState(value, serializer())

class FunState<T>(private var value: T, val serializer: KSerializer<T>) /*: FunState<T>, */: KoinComponent, ReadWriteProperty<Fun, T> {
    private var registered: Boolean = false

    fun receiveUpdate(value: NetworkValue) {
        this.value = Json.decodeFromString(serializer, value)
    }


    override fun getValue(thisRef: Fun, property: KProperty<*>): T {
        if (!registered) {
            registered = true
            thisRef.client.registerState(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunState<Any?>
            )
            thisRef.client.setPendingValue(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunState<Any?>
            )
        }

        return value
    }

    override fun setValue(thisRef: Fun, property: KProperty<*>, value: T) {
        if (!registered) {
            registered = true
            thisRef.client.registerState(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunState<Any?>
            )
        }

        this.value = value

        thisRef.client.sendUpdate(
            holderKey = thisRef.id,
            propertyKey = property.name,
            value = value,
            serializer
        )
    }

}

class LocalMultiplayer(private val playerCount: Int) {
    val clients: List<FunClient> = List(playerCount) { clientNum ->
        val x = object : FunCommunication {
            override fun <T> send(holderKey: String, propertyKey: String, value: T, serializer: KSerializer<T>) {
                val asJson = Json.encodeToString(serializer, value)

                repeat(playerCount) {
                    if (clientNum != it) {
                        clients[it].receiveUpdate(holderKey, propertyKey, asJson)
                    }
                }
            }
        }
        FunClient(x)
    }
}

val multiplayer = LocalMultiplayer(2)

val client1World = World("my-world", multiplayer.clients[0])

val client2World = World("my-world", multiplayer.clients[1])


fun main() {
    gameLogic()
}

fun gameLogic() {

    println("Before = " + client2World.width)
    client1World.width = 1000
    println("After = " + client2World.width)
    client2World.width = 500
    println("After after: ${client2World.width}")

}

