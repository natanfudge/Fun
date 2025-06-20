package io.github.natanfudge.fn.mte

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.mte.Balance.MineInterval
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.physics.FunRenderState
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.renderState
import io.github.natanfudge.fn.render.InputManagerMod
import io.github.natanfudge.fn.render.Material
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.milliseconds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
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

class Block(game: MineTheEarth, val type: BlockType, val pos: IntOffset) : Fun("${type}-${game.getNextBlockIndex(type)}", game.context) {
    companion object {
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
            breakOverlay = renderState(breakOverlays[damageIndex - 1], "break").apply {
                scale = Vec3f(1.01f, 1.01f, 1.01f) // It should be slightly around the cube
                position = render.position
            }
        } else {
            breakOverlay?.close()
            breakOverlay = null
        }
    }


    init {
        render.position = Vec3f(x = pos.x.toFloat(), y = 0f, z = pos.y.toFloat())
        physics.affectedByGravity = false
        physics.isImmovable = true
        updateBreakOverlay(health)
    }
}

//TODO: change hoverMod to reach the root fun, then find all child renders and set their tint.

// Old restart: ~30 ms


class Player(game: MineTheEarth) : Fun("Player", game.context) {
    companion object {
        val model = Model.fromGlbResource("files/models/hedgie_lowres.glb")
    }

    val physics = physics(renderState(model), game.physics)

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
                if (selectedBlock is Block && hasReachToBlock(selectedBlock)) {
                    selectedBlock.health -= 1f
                }
            }
        })
    }

    val intPos2D get() = IntOffset(physics.position.x.toInt(), physics.position.z.toInt())

    fun hasReachToBlock(block: Block): Boolean {
        if (intPos2D.diagonalDistance(block.pos) > Balance.BreakReach) return false
        return true
    }

    private fun blocksInWay(startPos: IntOffset, endPos: IntOffset): List<Block> {
        TODO()
    }
}

/**
 * Returns the distance between two [IntOffset]s, with diagonals only counting as one unit.
 */
fun IntOffset.diagonalDistance(to: IntOffset): Int = max(abs(x - to.x), abs(y - to.y))


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

    val hoverMod = installMod(HoverHighlightMod(context) {
        it is Block && player.hasReachToBlock(it)
    })

    private val mapWidth = 21
    private val mapHeight = 21

    val blocks = List(mapHeight * mapWidth) {
        val x = it % mapWidth
        val y = it / mapWidth
        val type = if (Random.nextInt(1, 11) == 10) BlockType.Gold else BlockType.Dirt
        Block(this, type, IntOffset(x - mapWidth / 2, y - mapHeight / 2))
    }.associateWith { it.pos }

    init {
        physics.system.earthGravityAcceleration = 20f
        context.camera.setLookAt(Vec3f(0f, -8f, 11f), forward = Vec3f(0f, 1f, 0f))
        context.camera.focus(playerStartingPos, distance = 8f)



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