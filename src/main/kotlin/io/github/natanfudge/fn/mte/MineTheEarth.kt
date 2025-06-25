package io.github.natanfudge.fn.mte

import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.render.InputManagerMod
import io.github.natanfudge.fn.render.ScrollDirection
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random


/**
 * Returns the distance between two [IntOffset]s, with diagonals only counting as one unit.
 */
fun IntOffset.diagonalDistance(to: IntOffset): Int = max(abs(x - to.x), abs(y - to.y))


class MineTheEarth(override val context: FunContext) : FunApp() {

    private val indices = mutableMapOf<String, Int>()
    fun nextFunId(name: String): FunId {
        if (name !in indices) indices[name] = 0
        val nextIndex = indices.getValue(name)
        indices[name] = nextIndex + 1
        return "$name-$nextIndex"
    }


    private val nextBlockIndices = mutableMapOf<BlockType, Int>()

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

    private val mapWidth = 21
    private val mapHeight = 21

    val blocks = List(mapHeight * mapWidth) {
        val x = it % mapWidth
        val y = it / mapWidth
        val type = if (Random.nextInt(1, 11) == 10) BlockType.Gold else BlockType.Dirt
        Block(this, type, BlockPos(x - mapWidth / 2, y = 0, z = y - mapHeight / 2))
    }.associateBy { it.pos }.toMutableMap()

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
}


fun main() {
    startTheFun {
        { MineTheEarth(it) }
    }
}