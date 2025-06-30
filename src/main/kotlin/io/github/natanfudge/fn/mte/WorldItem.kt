package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.render
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.seconds
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
//TODO: gold items need to be re-exported in blender to not have Y-up

class WorldItem(val game: MineTheEarth, item: Item, pos: Vec3f) : Fun(game.nextFunId("Item-${item.type}"), game.context) {
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
    val render = render(models.getValue(item.type), physics)

    init {
        physics.scale = Vec3f(0.5f, 0.5f, 0.5f)
        physics.position = pos

        render.localTransform.rotation = render.localTransform.rotation.rotateY(PI.toFloat() / 2)


        game.animation.animateLoop(2.seconds) {
            val down = spring(it)
            render.localTransform.translation = Vec3f(x = 0f, y = 0f, z = down * 0.15f - 0.1f)
        }.closeWithThis()
    }

    override fun cleanup() {
        game.world.items.remove(this)
    }

}

fun linearLoop(t: Float): Float {
    if (t < 0.5f) return 2 * t
    return 2 * (1 - t)
}

fun spring(t: Float): Float {
    require(t in 0.0..1.0) { "t must be in [0, 1]" }
    return sin(t * PI).toFloat()          // half a sine wave
}