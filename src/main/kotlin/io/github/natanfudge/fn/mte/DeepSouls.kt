package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.mte.gui.MainMenu
import io.github.natanfudge.fn.mte.gui.addDsPanel
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.physics.translation
import io.github.natanfudge.fn.render.CameraMode
import io.github.natanfudge.wgpu4k.matrix.Vec3f


class MineTheEarthMainMenuTemp(override val context: FunContext) : FunApp() {
    val ds = DeepSouls(context)

    init {
        context.addDsPanel {
            MainMenu(ds)
        }
    }
}

class DeepSouls(context: FunContext) : Fun(context, "DeepSouls") {
    val inMainMenuState = funValue(true, "inMainMenu")
    var inMainMenu by inMainMenuState

    init {
        if (!inMainMenu) {
            MineTheEarthGame(context)
        }
    }
}


class MineTheEarthGame(override val context: FunContext) : FunApp() {

    val balance by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Balance.create()
    }

    val animation = FunAnimation(context)

    private val indices = mutableMapOf<String, Int>()
    fun nextFunId(name: String): FunId {
        if (name !in indices) indices[name] = 0
        val nextIndex = indices.getValue(name)
        indices[name] = nextIndex + 1
        return "$name-$nextIndex"
    }

    val input = InputManager(context)

    val physics = FunPhysics(context)
    val player = Player(this)


//    val whale = Whale(this)

    val hoverMod = HoverHighlight(context, redirectHover = {
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


    var cameraDistance = 15f

    private fun repositionCamera(playerPos: Vec3f) {
        if (creativeMovement.mode == CameraMode.Off) {
            context.camera.setLookAt(playerPos + Vec3f(0f, -cameraDistance, 0f), forward = Vec3f(0f, 1f, 0f))
            // This one is mostly for zooming in
            context.camera.focus(playerPos, distance = cameraDistance)
        }
    }

    val creativeMovement = CreativeMovement(context, input)

    fun initialize() {
        player.physics.position = Vec3f(0f, 0.5f, 11.5f)
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
        ErrorNotifications(context)
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