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
        val service = serviceKey<DeepSoulsGame>()
    }

    init {
        exposeAsService(service)
    }

    val balance by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Balance.create()
    }

    // TODO: add to dsl
    val background = Background()

    val player = Player(this)
    val devil = Devil()

    //TODO: Add to dsl

    val world = World(this)


    var cameraDistance by funValue(15f)

    private fun repositionCamera(playerPos: Vec3f) {
        if (baseServices.creativeMovement.mode == CameraMode.Off) {
            camera.setLookAt(playerPos + Vec3f(0f, -cameraDistance, 0f), forward = Vec3f(0f, 1f, 0f))
            // This one is mostly for zooming in
            camera.focus(playerPos, distance = cameraDistance)
        }
    }


    //TODO: Add to dsl

    fun initialize() {
        player.physics.position = Vec3f(0f, 0.5f, 110f)
//        player.physics.position = Vec3f(0f, 0.5f, 105f)
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


    }
}


fun main() {
    startTheFun(
        serviceConfig = {
            FunBaseServices(
                hoverHighlight = HoverHighlight(redirectHover = {
                    if (baseServices.visualEditor.enabled) it
                    else if (it is Block) {
                        DeepSoulsGame.service.current.player.targetBlock(it)
                    } else null
                }, hoverRenderPredicate = {
                    if (baseServices.visualEditor.enabled) true
                    // Don't highlight the break overlay
                    else !it.id.endsWith(Block.BreakRenderId)
                })
            )

        }
    ) {
        DeepSouls()
    }
}