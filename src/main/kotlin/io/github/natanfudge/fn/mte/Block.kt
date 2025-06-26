package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.physics.FunRenderState
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.render
import io.github.natanfudge.fn.render.Material
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.util.ceilToInt
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class Block(private val game: MineTheEarth, val type: BlockType, val pos: BlockPos) : Fun(game.nextFunId("Block-$type"), game.context) {
    companion object {
        val BreakRenderId = "break"
        val models = BlockType.entries.associateWith {
            Model(Mesh.HomogenousCube, "Block-$it", Material(FunImage.fromResource("drawable/blocks/${it.name.lowercase()}.png")))
        }

        val breakOverlays = (1..5).map {
            Model(Mesh.HomogenousCube, "Damage-$it", Material(FunImage.fromResource("drawable/break/break_$it.png")))
        }
    }

    val physics = physics(render(models.getValue(type)), game.physics)

    var breakOverlay: FunRenderState? = null

    var health by funValue(Balance.blockHardness(type), "health") {
        updateBreakOverlay(it)
    }

    private fun updateBreakOverlay(newHealth: Float) {
        val missingHpFraction = (1 - (newHealth / Balance.blockHardness(type))).coerceIn(0f, 1f)
        if (missingHpFraction == 1f) {
            destroy()
            return
        }

        if (missingHpFraction > 0) {
            val damageIndex = (missingHpFraction * 5).ceilToInt().coerceAtMost(5)
            breakOverlay?.close()
            breakOverlay = render(breakOverlays[damageIndex - 1], BreakRenderId).apply {
                scale = Vec3f(1.01f, 1.01f, 1.01f) // It should be slightly around the cube
                position = physics.position
            }
        } else {
            breakOverlay?.close()
            breakOverlay = null
        }
    }


    init {
        physics.position = pos.toVec3()
        physics.affectedByGravity = false
        physics.isImmovable = true
        updateBreakOverlay(health)
    }

    fun destroy() {
        if (type == BlockType.Gold) {
            game.world.spawnItem(Item(ItemType.GoldOre, 1), physics.position)
        }
        close()
    }

    override fun cleanup() {
        game.world.blocks.remove(pos)
    }
}

