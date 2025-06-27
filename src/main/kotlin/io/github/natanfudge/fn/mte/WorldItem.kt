package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.render
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.seconds
import kotlin.math.PI
import kotlin.math.sin

enum class ItemType {
    GoldOre
}

data class Item(
    val type: ItemType,
    val count: Int,
)

class WorldItem(val game: MineTheEarth, val item: Item, pos: Vec3f) : Fun(game.nextFunId("Item-${item.type}"), game.context) {
    companion object {
        val models = ItemType.entries.associateWith {
            Model.fromGlbResource("files/models/items/${it.name.lowercase()}.glb")
        }
    }

    val render = render(models.getValue(item.type)).apply { scale = Vec3f(0.5f, 0.5f, 0.5f) }
    val physics = physics(render, game.physics)

    init {
        physics.position = pos.copy(z = pos.z - 0.2f)
        physics.rotation = physics.rotation.rotateY(PI.toFloat() / 2)

        game.animation.animateLoop(2.seconds) {
            val down = spring(it)
            render.position = pos.copy(z = physics.position.z + down * 0.1f)
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