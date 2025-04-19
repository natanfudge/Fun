package io.github.natanfudge.fn.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.component.KoinComponent
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


class WorldStateHolder(val world: World) : FunStateHolder {
    //I think we can avoid StateHolders and such in production and non-synced values
    override fun setValue(key: String, value: Any?) {
        when (key) {
            "width" -> world._width.value = value as Int
            else -> error("Unsupported")
        }
    }

}

class World(id: String, client: FunClient) : SomeFun(id, client, { WorldStateHolder(it as World) }) {
    val world = mutableStateListOf<Player>()
    //TODO: need to think how I can unify this API...
    //TOdo: there's also the issue that funState exposes an impl instance now so we can modify it
    //TODO: I think right now, it's best to keep the by funState() slow api, and in the future transform it into a normal property
    // and we'll inject stuff into the get() set() of the property to get what we want.
    val _width = funState(100, usingAutoState = false)
    var width by _width
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


class FunClient {

//    private val values = mutableMapOf<String, FunState<*>>()


    private val stateHolders = mutableMapOf<String, FunStateHolder>()
    fun update(holderKey: String, propertyKey: String, value: Any?) {
        //TODO: omega hack
        if (this == client1) {
            //TODO: handle nulls better
            client2.stateHolders[holderKey]?.setValue(propertyKey, value)
        } else {
            client1.stateHolders[holderKey]?.setValue(propertyKey, value)
        }
    }

    fun register(fn: Fun, state: FunStateHolder) {
        if (fn.id in stateHolders) {
            throw IllegalArgumentException("A state holder with the id '${fn.id}' was registered twice. Make sure to give components unique IDs. ")
        }
        stateHolders[fn.id] = state
    }

//    fun register(fn: MapFun)

    //TODO: maybe we need some type safety

    /**
     * A pending value will get DELETED once it is retrieved!
     */
    fun getPendingValue(holderKey: String, propertyKey: String, state: FunState<Any?>): Any? {
        //TODO: handle nulls better
        return (stateHolders[holderKey] as MapStateHolder).getPendingValue(propertyKey, state)
    }

    fun registerState(holderKey: String, propertyKey: String, state: FunState<Any?>) {
        //TODO: handle missing/mismatching values better
        (stateHolders[holderKey] as MapStateHolder).registerState(propertyKey, state)
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
    private val pendingValues = mutableMapOf<String, Any?>()

    private val map = mutableMapOf<String, FunState<Any?>>()
    override fun setValue(key: String, value: Any?) {
        if (key in map) {
            // Property was properly registered, update it
            (map[key] as? FunStateImpl<Any?>)?.value = value
        } else {
            // Not registered yet, wait for it to be registered to grant it the value
            pendingValues[key] = value
        }
    }

    fun registerState(key: String, value: FunState<Any?>) {
        map[key] = value
    }

    fun getPendingValue(key: String, state: FunState<Any?>): Any? {
        val value = pendingValues[key] ?: return null
        pendingValues.remove(key)
        map[key] = state
        return value
    }
}


/**
 * If you implement it manually it could be more performant than [MapStateHolder]
 */
interface FunStateHolder {
    fun setValue(key: String, value: Any?)
}


class UnfunStateException(message: String) : IllegalStateException(message)

interface Fun {
    val id: String
    val client: FunClient
}

//TODO: we could have a SinglePlayerFun that doesn't require specifying a client.
//TODO: need to see if we need to have a type argument here for this to work well
abstract class SomeFun(
    final override val id: String,
    final override val client: FunClient,
    stateHolder: (SomeFun) -> FunStateHolder = { MapStateHolder() },// In the future this could be like getAutoStateHolder<T>()
) : Fun {
    init {
        client.register(this, stateHolder(this))
    }
}


interface FunState<T> : ReadWriteProperty<Fun, T>

//TODO: i'm sure i could do someting better than this usingAutoState parameter, this is just for testing
fun <T> Fun.funState(value: T, usingAutoState: Boolean = true): FunStateImpl<T> = FunStateImpl(value, registered = !usingAutoState)

 class FunStateImpl<T>(var value: T, private var registered: Boolean = false) : FunState<T>, KoinComponent {



    override fun getValue(thisRef: Fun, property: KProperty<*>): T {
        //TODO: registered checks can be avoided if we have a way to know the key of the property early, some compiler support.
        if (!registered) {
            registered = true
            thisRef.client.registerState(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunState<Any?>
            )
            val pending = thisRef.client.getPendingValue(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunState<Any?>
            )
            if (pending != null) value = pending as T

        }



        return value
    }

    override fun setValue(thisRef: Fun, property: KProperty<*>, value: T) {
        //TODO: registered checks can be avoided if we have a way to know the key of the property early, some compiler support.
        if (!registered) {
            registered = true
            thisRef.client.registerState(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunState<Any?>
            )
        }

        this.value = value

        thisRef.client.update(
            holderKey = thisRef.id,
            propertyKey = property.name,
            value = value
        )
    }

}

val client1 = FunClient()
val client2 = FunClient()
val client1World = World("my-world", client1)

val client2World = World("my-world", client2)


fun main() {
    gameLogic()
}

fun gameLogic() {

    println("Before = " + client2World.width)
    client1World.width = 1000
    println("After = " + client2World.width)

}

