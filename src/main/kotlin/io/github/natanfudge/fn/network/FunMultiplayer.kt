package io.github.natanfudge.fn.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


class World(id: String) : Fun(id) {
    val world = mutableStateListOf<Player>()
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

//interface FunStateManager<T: StateChange> {
//    fun update(change: T)
//}

sealed interface StateChange {
    class Direct(val key: String, val value: Any?) : StateChange
    class ByHolder(val holderKey: String, val propertyKey: String, val value: Any?) : StateChange
}


class FunStateManager(private val id: String) {

//    private val values = mutableMapOf<String, FunState<*>>()


    //TODO: need to think about multithreading implications
    private val stateHolders = mutableMapOf<String, FunStateHolder>()
    fun update(holderKey: String, propertyKey: String, value: Any?) {
        //TODO: omega hack
        if (this == client1Manager) {
            //TODO: handle nulls better
            client2Manager.stateHolders[holderKey]?.setValue(propertyKey, value)
        } else {
            client1Manager.stateHolders[holderKey]?.setValue(propertyKey, value)
        }

//        val holder =
        //TODO: handle nulls better

//        when (change) {
//            is StateChange.ByHolder -> stateHolders[change.holderKey]?.setValue(change.propertyKey, change.value)
//            is StateChange.Direct -> (values[change.key] as? FunState<Any?>)?.value = change.value
//        }
    }

    fun register(fn: Fun, state: FunStateHolder) {
        //TODO: probably hack
        fn.clientScope = id
        if (fn.id in stateHolders) {
            throw IllegalArgumentException("A state holder with the id '${fn.id}' was registered twice. Make sure to give components unique IDs. ")
        }
        stateHolders[fn.id] = state
    }

    //TODO: maybe we need some type safety
    fun getPendingValue(holderKey: String, propertyKey: String, state: FunState<Any?>): Any? {
        //TODO: handle nulls better
        return stateHolders[holderKey]?.getPendingValue(propertyKey, state)
    }


//    fun get(holderKey: String, propertyKey: String): Any? {
//
//    }
}

//TODO: think about 'secret' values - values only visible to their owner.

//class LeanFunStateManager: FunStateManager<StateChange.ByHolder> {
//    override fun update(change: StateChange.ByHolder) {
//        stateHolders[change.holderKey]?.setValue(change.propertyKey, change.value)
//    }
//}

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

    override fun getPendingValue(key: String, state: FunState<Any?>): Any? {
        val value = pendingValues[key] ?: return null
        pendingValues.remove(key)
        map[key] = state
        return value
    }

//    fun registerProperty()
}


//TODO: think about how we want to store state identifiers
// TODO: need to think about we do secure state changes (not anyone can do it lul)

/**
 * If you implement it manually it could be more performant than [MapStateHolder]
 */
//TODO: maybe FunStateHolder and Fun are the same thing? we can merge them? at least have one extend the other
interface FunStateHolder {
    fun setValue(key: String, value: Any?)
    fun getPendingValue(key: String, state: FunState<Any?>): Any? {
        return null
    }
}

//TODO: maybe we can compose Funs in some way for a tree structure in state
abstract class Fun(
    val id: String,
) {
    // TODO: there's probably  a better way to do this
    var clientScope: String? = null
//    internal val stateHolder = MapStateHolder()
}

interface FunState<T> : ReadWriteProperty<Fun, T>

fun <T> funState(value: T): FunState<T> = FunStateImpl(value)

// Right now i'm going to go all out with ineffiencies, this is internal API anyway.
internal class FunStateImpl<T>(
    var value: T,

    ) : FunState<T>, KoinComponent {
    /**
     * SLOW: Might be best to store this elsewhere. This is extra memory cost
     */
    private var stateManager: FunStateManager? = null

//    val stateManager: FunStateManager by inject<FunStateManager>()


    override fun getValue(thisRef: Fun, property: KProperty<*>): T {
        if (stateManager == null) { //TODO: duplicated code
            stateManager = inject<FunStateManager>(thisRef.clientScope?.let { named(it) }).value
        }
        //TODO: getPendingValue can be avoided if we have a way to know the key of the property early, some compiler support.
        val pending = stateManager!!.getPendingValue(
            holderKey = thisRef.id,
            propertyKey = property.name,
            state = this as FunState<Any?>
        )
        if (pending != null) value = pending as T

        return value
    }

    override fun setValue(thisRef: Fun, property: KProperty<*>, value: T) {
        if (stateManager == null) {
            stateManager = inject<FunStateManager>(thisRef.clientScope?.let { named(it) }).value
        }
        stateManager!!.update(
            holderKey = thisRef.id,
            propertyKey = property.name,
            value = value
        )
    }

}

val client1World = World("my-world")

//val client1Holder = MapStateHolder()
val client2World = World("my-world")
//val client2Holder = MapStateHolder()

//TODO: koin
val client1Manager = FunStateManager("Client1")
val client2Manager = FunStateManager("Client2")

fun main() {
    val appModule = module {
        single<FunStateManager>(named("Client1")) {
            client1Manager
        }
        single<FunStateManager>(named("Client2")) {
            client2Manager
        }
//        scope<FunStateManager> {
//            scoped(named("Client1")) { client1Manager }
//            scoped(named("Client2")) { client2Manager }
//        }
//        single<FunStateManager> { client1Manager }
    }

    startKoin { modules(appModule) }


    gameLogic()
}

//fun register(fn: Fun) {
//    manager.update()
//}

//TODO: this won't work very well with multithreading multiple clients
inline fun inClient1Scope(func: () -> Unit) {
    val scope = getKoin()
        .createScope("client1-scope", named("Client1"))
    func()
    scope.close()
}

inline fun inClient2Scope(func: () -> Unit) {
    val scope = getKoin()
        .createScope("client2-scope", named("Client2"))
    func()
    scope.close()
}

fun gameLogic() {

    client1Manager.register(client1World, MapStateHolder())
    client2Manager.register(client2World, MapStateHolder())

    println("Before = " + client2World.width)
    client1World.width = 1000
    println("After = " + client2World.width)


//    val sword = Sword()
//
//    sword.enchantment.strength = 2


//    client1World

}

