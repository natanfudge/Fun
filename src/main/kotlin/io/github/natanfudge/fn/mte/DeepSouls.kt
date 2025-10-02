package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.mte.gui.MainMenu
import io.github.natanfudge.fn.mte.gui.addDsHudPanel
import io.github.natanfudge.fn.render.CameraMode
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class DeepSouls() : Fun("DeepSouls") {
    val inMainMenuState = funValue("inMainMenu", { true }) {}
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

    val background = Background()

    val player = Player(this)
    val devil = Devil()

    val world = World(this)


    var cameraDistance by funValue(15f)

    private fun repositionCamera(playerPos: Vec3f) {
        if (baseServices.creativeMovement.mode == CameraMode.Off) {
            camera.setLookAt(playerPos + Vec3f(0f, -cameraDistance, 0f), forward = Vec3f(0f, 1f, 0f))
            // This one is mostly for zooming in
            camera.focus(playerPos, distance = cameraDistance)
        }
    }

    fun initialize() {
        player.physics.position = Vec3f(0f, 0.5f, 110f)
        player.animation.play("jump", loop = false)

        world.initialize()
    }

    init {
        baseServices.visualEditor.enabled = false
        baseServices.physics.system.earthGravityAcceleration = 20f

        player.physics.positionState.afterChange {
            repositionCamera(it)
        }


        input.registerHotkey("Zoom Out", ScrollDirection.Down, ctrl = true) {
            cameraDistance += 1f
            repositionCamera(player.physics.position)
        }


        input.registerHotkey("Zoom In", ScrollDirection.Up, ctrl = true) {
            cameraDistance -= 1f
            repositionCamera(player.physics.position)
        }

        baseServices.hoverHighlight.redirectHover = {
            if (baseServices.visualEditor.enabled) it
            else if (it is Block) {
                player.targetBlock(it)
            } else null
        }

        baseServices.hoverHighlight.hoverRenderPredicate = {
            if (baseServices.visualEditor.enabled) true
            // Don't highlight the break overlay
            else !it.id.endsWith(Block.BreakRenderId)
        }
    }
}


fun main() {
    startTheFun {
        DeepSouls()
    }
}