package io.github.natanfudge.fn.mte

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunOld
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.FunState
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.FunRenderState
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.physics
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.sin

enum class ItemType {
    GoldOre,
    Nothing
}

data class Item(
    val type: ItemType,
    val count: Int,
) {
    init {
        if (type == ItemType.Nothing) {
            check(count == 0) { "The Nothing item should have a count of 0" }
        } else {
            check(count > 0) { "A regular item should have a positive count" }
        }
    }
}

class XD

class Box<T>(
    var value: T,
    val exists: Boolean
)

class FakeNullabilityMutableState<T>: MutableState<T> {
    private var _value: T? = null

    override var value: T
        get() = _value as T
        set(value) {
            _value = value
        }

    override fun component1(): T {
        return value
    }

    override fun component2(): (T) -> Unit {
        return {
            value = it
        }
    }

}

//TODO: employ this type gymnastic for
@Serializable
class Foo {
    var x by FakeNullabilityMutableState<Int?>()

    constructor(x: Int) {
        this.x = x
    }

    fun x(): Int? {
        return x
    }
}

//@Serializable
//class Bar(x: Int) {
//    @Transient
//    val x: MutableState<Int> = mutableStateOf(x)
//}

fun main() {
    val foo = Foo(1)
    println(foo.x())
    val json = Json.encodeToString(foo)
    val back = Json.decodeFromString<Foo>(json)
    println(json)
    print(back)
}

//@Serializable
class WorldItemState private constructor(override val id: String, val itemType: ItemType) : Fun() {
    var itemCount by funValue<Int>(lateInit(),"itemCount")
    constructor(item: Item, id: String) : this(id, item.type) {
        itemCount = item.count
    }
}

//@Serializable
//class WorldItemState2(override val id: String, val itemType: ItemType) : Fun() {
//    @Transient
//    lateinit var itemCount: ClientFunValue<Int>
//    constructor(item: Item, id: String) : this(id, item.type) {
//        this.itemCount = funValue<Int>(item.count,"itemCount")
//    }
//}

fun <T> funValueFromConstructor(id: String): ClientFunValue<T> {
    TODO()
}

class WorldItem(val game: MineTheEarthGame, item: Item) : FunOld(game.context, game.nextFunId("Item-${item.type}")) {
    companion object {
        val models = ItemType.entries.filter { it != ItemType.Nothing }.associateWith {
            Model.fromGlbResource("files/models/items/${it.name.lowercase()}.glb")
        }
    }

    /**
     * We allow setting the count dynamically for efficiency. For setting the type, we can just destroy and respawn the item.
     */
    var itemCount by funValue(item.count, "itemCount")

    val itemType = item.type

    val item get() = Item(itemType, itemCount)


    val physics = physics(game.physics.system)
    val renderState = FunRenderState(id.child("render"))
    val render = render(models.getValue(item.type), physics)

    init {
        physics.scale = Vec3f(0.5f, 0.5f, 0.5f)

        render.localTransform.rotation = render.localTransform.rotation.rotateZ(PI.toFloat() / 2)


        game.animation.animateLoop(2.seconds) {
            val down = spring(it)
            render.localTransform.translation = Vec3f(x = 0f, y = 0f, z = down * 0.15f)
        }.closeWithThis()

        physics.onTransformChange {
            // Despawn items that have fallen out of the world
            if (it.translation.z < WORLD_LOWEST_Z) close()
        }
    }

    override fun cleanup() {
        game.world.items.remove(this)
    }

}

const val WORLD_LOWEST_Z = -200

fun linearLoop(t: Float): Float {
    if (t < 0.5f) return 2 * t
    return 2 * (1 - t)
}

fun spring(t: Float): Float {
    require(t in 0.0..1.0) { "t must be in [0, 1]" }
    return sin(t * PI).toFloat()          // half a sine wave
}