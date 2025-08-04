package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.funValue
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.mte.gui.MainMenu
import io.github.natanfudge.fn.mte.gui.addDsHudPanel
import io.github.natanfudge.fn.physics.translation
import io.github.natanfudge.fn.render.CameraMode
import io.github.natanfudge.wgpu4k.matrix.Vec3f


class DeepSouls() : Fun("DeepSouls") {
    val inMainMenuState = funValue("inMainMenu", {true}){}
    var inMainMenu by inMainMenuState

    init {
        if (!inMainMenu) {
            DeepSoulsGame()
        }

        addDsHudPanel {
            MainMenu(this@DeepSouls)
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
    // TODO: restore
//    val background = Background()


    val input = InputManager(context)

    val physics = FunPhysics()
    val player = Player(this)

    val devil = Devil()


//    val whale = Whale(this)

    val hoverMod = HoverHighlight(redirectHover = {
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


    var cameraDistance by funValue(15f)

    private fun repositionCamera(playerPos: Vec3f) {
        if (creativeMovement.mode == CameraMode.Off) {
            context.camera.setLookAt(playerPos + Vec3f(0f, -cameraDistance, 0f), forward = Vec3f(0f, 1f, 0f))
            // This one is mostly for zooming in
            context.camera.focus(playerPos, distance = cameraDistance)
        }
    }

    val creativeMovement = CreativeMovement(input)

    fun initialize() {
        player.physics.position = Vec3f(0f, 0.5f, 110f)
//        player.physics.position = Vec3f(0f, 0.5f, 105f)
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

        FunDebugPanel()
        ErrorNotifications()
    }
}


fun main() {
    startTheFun {
        DeepSouls()
    }
}