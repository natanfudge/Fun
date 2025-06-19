package io.github.natanfudge.fn.mte

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
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.renderState
import io.github.natanfudge.fn.render.InputManagerMod
import io.github.natanfudge.fn.render.Material
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.PI
import kotlin.random.Random

enum class BlockType {
    Dirt, Gold
}

class Block(game: MineTheEarth, val type: BlockType, pos: IntOffset) : Fun("${type}-${game.nextBlockIndex++}", game.context) {
    companion object {
        private val unitCube = Mesh.UnitCube()
        val models = BlockType.entries.associateWith {
            Model(unitCube, "Block-$it", Material(FunImage.fromResource("drawable/blocks/${it.name.lowercase()}.png")))
        }
    }


    val render = renderState(models.getValue(type))
    val physics = physics(render, game.physics)

    init {
        render.position = Vec3f(x = pos.x.toFloat(), y = 0f, z = pos.y.toFloat())
        physics.affectedByGravity = false
        physics.isImmovable = true
    }
}

class Player(game: MineTheEarth) : Fun("Player", game.context) {
    val render = renderState(Model.fromGlbResource("files/models/miner.glb"))
    val physics = physics(render, game.physics)
}


// TODO: next thing: Improve InputManager for hotkeys!

class MineTheEarth(override val context: FunContext) : FunApp() {
    var nextBlockIndex = 0
    val inputManager = installMod(InputManagerMod())

    val physics = installMod(PhysicsMod())

    init {
        val playerStartingPos = Vec3f(x = 0f, y = 0f, z = 11f)
        context.camera.setLookAt(Vec3f(0f, -8f, 11f), forward = Vec3f(0f, 1f, 0f))
        context.camera.focus(playerStartingPos, distance = 8f)

        installMods(
            CreativeMovementMod(context, inputManager),
            VisualEditorMod(context),
            RestartButtonsMod()
        )

        val player = Player(this)
        player.render.position = playerStartingPos
        player.render.rotation = player.render.rotation

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