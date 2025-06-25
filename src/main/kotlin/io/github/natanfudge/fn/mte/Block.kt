package io.github.natanfudge.fn.mte

import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.physics.FunRenderState
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.renderState
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


    val render = renderState(models.getValue(type))
    val physics = physics(render, game.physics)

    var breakOverlay: FunRenderState? = null

    var health by funValue(Balance.blockHardness(type), "health") {
        updateBreakOverlay(it)
    }

    private fun updateBreakOverlay(newHealth: Float) {
        val missingHpFraction = (1 - (newHealth / Balance.blockHardness(type))).coerceIn(0f, 1f)
        if (missingHpFraction == 1f) {
            close()
            return
        }

        if (missingHpFraction > 0) {
            val damageIndex = (missingHpFraction * 5).ceilToInt().coerceAtMost(5)
            breakOverlay?.close()
            breakOverlay = renderState(breakOverlays[damageIndex - 1], BreakRenderId).apply {
                scale = Vec3f(1.01f, 1.01f, 1.01f) // It should be slightly around the cube
                position = render.position
            }
        } else {
            breakOverlay?.close()
            breakOverlay = null
        }
    }


    init {
        render.position = pos.toVec3()
        physics.affectedByGravity = false
        physics.isImmovable = true
        updateBreakOverlay(health)
    }

    override fun cleanup() {
        game.blocks.remove(pos)
    }
}

class Item(private val game: MineTheEarth): Fun(game.nextFunId("item"), game.context) {
}