package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Quatf
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
    override fun toString(): String {
        return "$type X $count"
    }
    init {
        if (type == ItemType.Nothing) {
            check(count == 0) { "The Nothing item should have a count of 0" }
        } else {
            check(count > 0) { "A regular item should have a positive count" }
        }
    }
}


class WorldItem(val game: DeepSoulsGame, id: String, item: Item?) : Fun(id) {
    companion object {
        val models = ItemType.entries.filter { it != ItemType.Nothing }.associateWith {
            Model.fromGlbResource("files/models/items/${it.name.lowercase()}.glb")
        }
    }

    /**
     * We allow setting the count dynamically for efficiency. For setting the type, we can just destroy and respawn the item.
     */
    var itemCount by funValue(item?.count)
    val itemType by funValue(item?.type)


    val item get() = Item(itemType, itemCount)


    val physics = physics(game.physics.system)
    val render by render(models.getValue(itemType), physics)

    init {
        physics.scale = Vec3f(0.5f, 0.5f, 0.5f)

        render.localTransform.rotation = Quatf.identity().rotateZ(PI.toFloat() / 2)


        game.animation.animateLoop(2.seconds) {
            val down = spring(it)
            render.localTransform.translation = Vec3f(x = 0f, y = 0f, z = down * 0.15f)
        }.closeWithThis()

        physics.positionState.afterChange {
            // Despawn items that have fallen out of the world
            if (!deleted) {
                if (it.z < WORLD_LOWEST_Z) {
                    delete()
                    deleted = true
                }
            }
        }
    }

    private var deleted = true

    fun delete() {
        game.world.items.remove(this)
        close()
    }
}

const val WORLD_LOWEST_Z = -200


fun spring(t: Float): Float {
    require(t in 0.0..1.0) { "t must be in [0, 1]" }
    return sin(t * PI).toFloat()          // half a sine wave
}