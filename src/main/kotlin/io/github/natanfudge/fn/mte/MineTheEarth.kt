package io.github.natanfudge.fn.mte

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.base.CreativeMovementMod
import io.github.natanfudge.fn.base.PhysicsMod
import io.github.natanfudge.fn.base.RestartButtonsMod
import io.github.natanfudge.fn.base.VisualEditorMod
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.gltf.fromGlbResource
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
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.random.Random

object Balance {
    fun blockHardness(type: BlockType): Float = when (type) {
        BlockType.Dirt -> 2f
        BlockType.Gold -> 5f
    }
}

enum class BlockType {
    Dirt, Gold
}

fun Float.ceilToInt(): Int = ceil(this).toInt()

class Block(game: MineTheEarth, val type: BlockType, pos: IntOffset) : Fun("${type}-${game.getNextBlockIndex(type)}", game.context) {
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
    // TODO: I need to stop trying to retain rendering stuff and close things properly on app restart.

    var health by funValue(Balance.blockHardness(type), "health") {
        val missingHpFraction = 1 - (it.coerceAtMost(Balance.blockHardness(type)) / Balance.blockHardness(type))
        if (missingHpFraction > 0) {
            val damageIndex = (missingHpFraction * 5).ceilToInt()
            breakOverlay?.close()
            breakOverlay = renderState(breakOverlays[damageIndex - 1], "break").apply {
                scale = Vec3f(1.001f, 1.001f, 1.001f) // It should be slightly around the cube
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
    }
}
// Old restart: ~30 ms


class Player(game: MineTheEarth) : Fun("Player", game.context) {
    companion object {
        val model = Model.fromGlbResource("files/models/miner.glb")
    }

    val physics = physics(renderState(model), game.physics)

    private val baseRotation = physics.rotation

    init {
        physics.position = game.playerStartingPos

        game.inputManager.registerHotkey(
            "Left", Key.A, onHold = {
                physics.rotation = baseRotation.rotateZ(PI.toFloat() / 2)
                physics.position -= Vec3f(it * 3, 0f, 0f)
            },
            onRelease = {
                physics.rotation = baseRotation
            }
        )

        game.inputManager.registerHotkey(
            "Right", Key.D,
            onHold = {
                physics.rotation = baseRotation.rotateZ(PI.toFloat() / -2)
                physics.position += Vec3f(it * 3, 0f, 0f)
            },
            onRelease = {
                physics.rotation = baseRotation
            }
        )

        game.inputManager.registerHotkey("Jump", Key.Spacebar, onPress = {
            if (physics.isGrounded()) {
                physics.velocity += Vec3f(0f, 0f, 8f)
            }
        })
    }
}

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
    val inputManager = installMod(InputManagerMod())

    val physics = installMod(PhysicsMod())

    init {
        physics.system.earthGravityAcceleration = 20f
        context.camera.setLookAt(Vec3f(0f, -8f, 11f), forward = Vec3f(0f, 1f, 0f))
        context.camera.focus(playerStartingPos, distance = 8f)



        installMods(
            CreativeMovementMod(context, inputManager),
            VisualEditorMod(this),
            RestartButtonsMod(context)
        )

        val player = Player(this)


        for (x in -10..10) {
            for (y in (-10..10)) {
                val type = if (Random.nextInt(1, 11) == 10) BlockType.Gold else BlockType.Dirt
                Block(this, type, IntOffset(x, y))
            }
        }


    }
}


fun main() {
    startTheFun {
        { MineTheEarth(it) }
    }
}