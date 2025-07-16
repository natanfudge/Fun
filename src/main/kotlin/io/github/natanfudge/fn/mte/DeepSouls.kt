package io.github.natanfudge.fn.mte

import androidx.compose.material3.Text
import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.mte.gui.MainMenu
import io.github.natanfudge.fn.mte.gui.addDsPanel
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.physics.translation
import io.github.natanfudge.fn.render.CameraMode
import io.github.natanfudge.wgpu4k.matrix.Vec3f


class MineTheEarthMainMenuTemp(override val context: FunContext) : FunApp() {
    val ds = DeepSouls()

    init {
        context.addDsPanel {
            MainMenu(ds)
        }
    }
}

class DeepSouls() : Fun("DeepSouls") {
    val inMainMenuState = funValue(true, "inMainMenu")
    var inMainMenu by inMainMenuState

    init {
        if (!inMainMenu) {
            DeepSoulsGame()
        }
    }
}


class DeepSoulsGame : Fun("Game") {
    companion object {
        val SurfaceZ = 100
    }

    val balance by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Balance.create()
    }

    val animation = FunAnimation()


    val input = InputManager(context)

    val physics = FunPhysics()
    val player = Player(this)

    val devil = Devil()

    val background = Background()


//    val whale = Whale(this)

    val hoverMod = HoverHighlight( redirectHover = {
        if (visualEditor.enabled) it
        else if (it is Block) player.targetBlock(it)
        else null
    }, hoverRenderPredicate = {
        if (visualEditor.enabled) true
        // Don't highlight the break overlay
        else !it.id.endsWith(Block.BreakRenderId)
    })

    val visualEditor: VisualEditor = VisualEditor(hoverMod, input, enabled = false)

    val world = World(this)


    var cameraDistance by funValue(15f, "cameraDistance")

    private fun repositionCamera(playerPos: Vec3f) {
        if (creativeMovement.mode == CameraMode.Off) {
            context.camera.setLookAt(playerPos + Vec3f(0f, -cameraDistance, 0f), forward = Vec3f(0f, 1f, 0f))
            // This one is mostly for zooming in
            context.camera.focus(playerPos, distance = cameraDistance)
        }
    }

    val creativeMovement = CreativeMovement(input)

    fun initialize() {
        player.physics.position = Vec3f(0f, 0.5f, 750f)
        player.animation.play("jump", loop = false)

        world.initialize()
    }
    init {
        physics.system.earthGravityAcceleration = 20f

        player.render.onTransformChange {
            repositionCamera(it.translation)
        }

        input.registerHotkey("Zoom Out", ScrollDirection.Down, ctrl = true) {
            cameraDistance += 1f
            repositionCamera(player.render.translation)
        }

        input.registerHotkey("Zoom In", ScrollDirection.Up, ctrl = true) {
            cameraDistance -= 1f
            repositionCamera(player.render.translation)
        }

        RestartButtons(context)
        ErrorNotifications()

        context.gui.addWorldPanel(Vec3f(3f, 0.5f, SurfaceZ.toFloat())) {
            Text("Halo World Panel")
        }
    }
}


fun main() {
    startTheFun {
        {
            MineTheEarthMainMenuTemp(it)
//            MineTheEarth(it)
        }
    }
}