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
import io.github.natanfudge.fn.hotreload.fromGlbResource
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.renderState
import io.github.natanfudge.fn.render.InputManagerMod
import io.github.natanfudge.fn.render.Material
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.random.Random

enum class BlockType {
    Dirt, Gold
}

class Block(game: MineTheEarth, val type: BlockType, val pos: IntOffset) : Fun("${type}-${game.nextBlockIndex++}", game.context) {
    companion object {
        private val unitCube = Mesh.UnitCube()
        val models = BlockType.entries.associateWith {
            Model(unitCube, "Block-$it", Material(FunImage.fromResource("drawable/blocks/${it.name.lowercase()}.png")))
        }
    }


    val render = renderState(models.getValue(type))
    val physics = physics(render, game.physics.system)

    init {
        render.position = Vec3f(x = pos.x.toFloat(), y = 0f, z = pos.y.toFloat())
        physics.affectedByGravity = false
        physics.isImmovable = true
    }
}

class Player(game: MineTheEarth) : Fun("Player", game.context) {
//    val render = renderState(Model.fromGlbResource("files/models/cube.glb"))
    val render = renderState(Model.fromGlbResource("files/models/miner.glb"))
    val physics = physics(render, game.physics.system)
}

//TODO: 1. Rotation thing
// 3. Fix texture

class MineTheEarth(override val context: FunContext) : FunApp() {
    var nextBlockIndex = 0
    val inputManager = installMod(InputManagerMod())

    val physics = installMod(PhysicsMod())

    init {
        context.camera.setLookAt(Vec3f(0f, -20f, 0f), forward = Vec3f(0f, 1f, 0f))
        installMods(
            CreativeMovementMod(context, inputManager),
            VisualEditorMod(context),
            RestartButtonsMod()
        )

        val player = Player(this)
        player.render.position = Vec3f(x = 0f, y = 0f, z = 12f)
        //TODO: IDK why but the player is upside down
        player.render.rotation = Quatf(0.77f,0.77f,0f,0f)
//        player.render.scale = Vec3f(10f,10f,10f)
//        player.physics.affectedByGravity =false

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