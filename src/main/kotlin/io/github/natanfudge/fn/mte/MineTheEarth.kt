package io.github.natanfudge.fn.mte

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.mte.Balance.MineInterval
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.renderState
import io.github.natanfudge.fn.render.InputManagerMod
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.milliseconds
import kotlin.math.*
import kotlin.random.Random

object Balance {
    fun blockHardness(type: BlockType): Float = when (type) {
        BlockType.Dirt -> 2f
        BlockType.Gold -> 6f
    }

    val MineInterval = 500.milliseconds

    val BreakReach = 5
}

enum class BlockType {
    Dirt, Gold
}

fun Float.ceilToInt(): Int = ceil(this).toInt()


//TODO: change hoverMod to reach the root fun, then find all child renders and set their tint.

// Old restart: ~30 ms


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

        game.input.registerHotkey("Jump", Key.Spacebar, onPress = {
            if (physics.isGrounded()) {
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

/**
 * Returns the distance between two [IntOffset]s, with diagonals only counting as one unit.
 */
fun IntOffset.diagonalDistance(to: IntOffset): Int = max(abs(x - to.x), abs(y - to.y))


//TODO: if we walk into the wall it registers as being on a floor, which stops falling and allows us to jump again.
// (it's because slightly walk into the wall, and then it looks like we are inside the wall. The fix is

//TODO: on Compose error, catch it instead of making it run loose and freeze the Compose menu

class MineTheEarth(override val context: FunContext) : FunApp() {
    private val nextBlockIndices = mutableMapOf<BlockType, Int>()
    val playerStartingPos = Vec3f(x = 0f, y = 0f, z = 11f)

    fun getNextBlockIndex(type: BlockType): Int {
        if (type in nextBlockIndices) {
            val next = nextBlockIndices[type]!!
            nextBlockIndices[type] = next + 1
            return next
        } else {
            nextBlockIndices[type] = 1
            return 0
        }
    }


    //    var nextBlockIndex = 0
    val input = installMod(InputManagerMod())

    val physics = installMod(PhysicsMod())

    val player = Player(this)

    val hoverMod = installMod(HoverHighlightMod(context, redirectHover = {
        if (it is Block) player.targetBlock(it)
        else null
    }, hoverRenderPredicate = {
        // Don't highlight the break overlay
        !it.id.endsWith(Block.BreakRenderId)
    }))

    private val mapWidth = 21
    private val mapHeight = 21

    val blocks = List(mapHeight * mapWidth) {
        val x = it % mapWidth
        val y = it / mapWidth
        val type = if (Random.nextInt(1, 11) == 10) BlockType.Gold else BlockType.Dirt
        Block(this, type, IntOffset(x - mapWidth / 2, y - mapHeight / 2))
    }.associateBy { it.pos }.toMutableMap()

    init {
        physics.system.earthGravityAcceleration = 20f

        player.render.positionState.change.listen {
            context.camera.setLookAt(it + Vec3f(0f,-8f,0f) , forward = Vec3f(0f, 1f, 0f))
            // This one is mostly for zooming in
            context.camera.focus(it, distance = 8f)
        }



        installMods(
            CreativeMovementMod(context, input),
            VisualEditorMod(hoverMod, input, enabled = false),
            RestartButtonsMod(context)
        )

    }
}


fun main() {
    startTheFun {
        { MineTheEarth(it) }
    }
}