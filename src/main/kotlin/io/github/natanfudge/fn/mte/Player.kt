package io.github.natanfudge.fn.mte

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.base.RateLimiter
import io.github.natanfudge.fn.base.getHoveredRoot
import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.mte.Balance.MineInterval
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.renderState
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt


class Player(private val game: MineTheEarth) : Fun("Player", game.context) {
    companion object {
        val model = Model.fromGlbResource("files/models/hedgie_lowres.glb")
    }

    val render = renderState(model)

    val physics = physics(render, game.physics)

    private val baseRotation = physics.rotation

    private val mineRateLimit = RateLimiter(game.context)

    init {
        physics.position = game.playerStartingPos

        game.input.registerHotkey(
            "Left", Key.A, onHold = {
                physics.rotation = baseRotation.rotateZ(PI.toFloat() / 2)
                physics.position -= Vec3f(it * 3, 0f, 0f)
            },
            onRelease = {
                physics.rotation = baseRotation
            }
        )

        game.input.registerHotkey(
            "Right", Key.D,
            onHold = {
                physics.rotation = baseRotation.rotateZ(PI.toFloat() / -2)
                physics.position += Vec3f(it * 3, 0f, 0f)
            },
            onRelease = {
                physics.rotation = baseRotation
            }
        )


        game.input.registerHotkey("Jump", Key.Spacebar, onHold = {
            if (physics.isGrounded) {
                physics.velocity += Vec3f(0f, 0f, 8f)
            }
        })

        game.input.registerHotkey("Break", PointerButton.Primary, onHold = {
            mineRateLimit.run(MineInterval) {
                val selectedBlock = context.getHoveredRoot()
                if (selectedBlock is Block) {
                    val target = targetBlock(selectedBlock)
                    if (target != null) {
                        target.health -= 1f
                    }
                }
            }
        })
    }

    val intPos2D get() = IntOffset(physics.position.x.roundToInt(), physics.position.z.roundToInt())

    /**
     * We target the first block near the player, because the selected block might be "covered" by the perspective of the player character.
     */
    fun targetBlock(directlyHoveredBlock: Block): Block? {
        if (intPos2D.diagonalDistance(directlyHoveredBlock.pos) > Balance.BreakReach) return null
        return firstBlockAlong(intPos2D, directlyHoveredBlock.pos)
    }

    private fun firstBlockAlong(startPos: IntOffset, endPos: IntOffset): Block? {
        var x0 = startPos.x
        var y0 = startPos.y
        val x1 = endPos.x
        val y1 = endPos.y

        // Δ values
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)

        // Step directions (+1 or -1)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1

        var err = dx + dy      // error term (≈ 0 at perfect diagonal)

        while (true) {
            val pointAlongTheWay = IntOffset(x0, y0)      // current cell
            val block = game.blocks[pointAlongTheWay]
            if (block != null) return block

            // reached destination → stop
            if (x0 == x1 && y0 == y1) break

            val e2 = 2 * err                 // doubled error for comparison
            if (e2 >= dy) {                  // step in X?
                err += dy
                x0 += sx
            }
            if (e2 <= dx) {                  // step in Y?
                err += dx
                y0 += sy
            }
        }
        return null
    }
}