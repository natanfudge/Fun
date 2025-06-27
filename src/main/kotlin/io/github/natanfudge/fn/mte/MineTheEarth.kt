package io.github.natanfudge.fn.mte

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.ComposePanelPlacer
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.render.InputManagerMod
import io.github.natanfudge.fn.render.ScrollDirection
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.abs
import kotlin.math.max


/**
 * Returns the distance between two [IntOffset]s, with diagonals only counting as one unit.
 */
fun IntOffset.diagonalDistance(to: IntOffset): Int = max(abs(x - to.x), abs(y - to.y))


class MineTheEarth(override val context: FunContext) : FunApp() {
    val animation = installMod(AnimationMod())

    private val indices = mutableMapOf<String, Int>()
    fun nextFunId(name: String): FunId {
        if (name !in indices) indices[name] = 0
        val nextIndex = indices.getValue(name)
        indices[name] = nextIndex + 1
        return "$name-$nextIndex"
    }

    val input = installMod(InputManagerMod())

    val physics = installMod(PhysicsMod())

    val player = Player(this)


    val hoverMod = installMod(HoverHighlightMod(context, redirectHover = {
        if (visualEditor.enabled) it
        else if (it is Block) player.targetBlock(it)
        else null
    }, hoverRenderPredicate = {
        if (visualEditor.enabled) true
        // Don't highlight the break overlay
        else !it.id.endsWith(Block.BreakRenderId)
    }))

    val visualEditor: VisualEditorMod = installMod(VisualEditorMod(hoverMod, input, enabled = false))

    val world = World(this)



    var cameraDistance = 8f

    private fun repositionCamera(playerPos: Vec3f) {
        context.camera.setLookAt(playerPos + Vec3f(0f, -cameraDistance, 0f), forward = Vec3f(0f, 1f, 0f))
        // This one is mostly for zooming in
        context.camera.focus(playerPos, distance = cameraDistance)
    }

    init {
        physics.system.earthGravityAcceleration = 20f

        player.render.positionState.onChange {
            repositionCamera(it)
        }

        input.registerHotkey("Zoom Out", ScrollDirection.Down, ctrl = true) {
            cameraDistance += 1f
            repositionCamera(player.render.positionState.value)
        }

        input.registerHotkey("Zoom In", ScrollDirection.Up, ctrl = true) {
            cameraDistance -= 1f
            repositionCamera(player.render.positionState.value)
        }


        installMods(
            CreativeMovementMod(context, input),
            RestartButtonsMod(context)
        )
    }

    @Composable
    override fun ComposePanelPlacer.gui() {
        with(player.inventory) {
            InventoryGUI()
        }
    }
}

// TODO: current new API i'm thinking of, is exposing MutEventStreams for all the usual stuff, as  well as a special API for injecting GUI panels.
// context.onFrame {}
// context.addPanel { }


fun main() {
    startTheFun {
        { MineTheEarth(it) }
    }
}