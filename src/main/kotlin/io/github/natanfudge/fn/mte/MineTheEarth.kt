package io.github.natanfudge.fn.mte

import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.render.InputManagerMod
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.milliseconds
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

object Balance {
    fun blockHardness(type: BlockType): Float = when (type) {
        BlockType.Dirt -> 2f
        BlockType.Gold -> 6f
    }

    val MineInterval = 500.milliseconds

    val BreakReach = 5
}

enum class BlockType {
    Dirt, Gold
}

fun Float.ceilToInt(): Int = ceil(this).toInt()



/**
 * Returns the distance between two [IntOffset]s, with diagonals only counting as one unit.
 */
fun IntOffset.diagonalDistance(to: IntOffset): Int = max(abs(x - to.x), abs(y - to.y))


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
    val input = installMod(InputManagerMod())

    val physics = installMod(PhysicsMod())

    val player = Player(this)

    val hoverMod = installMod(HoverHighlightMod(context, redirectHover = {
        if (it is Block) player.targetBlock(it)
        else null
    }, hoverRenderPredicate = {
        // Don't highlight the break overlay
        !it.id.endsWith(Block.BreakRenderId)
    }))

    private val mapWidth = 21
    private val mapHeight = 21

    val blocks = List(mapHeight * mapWidth) {
        val x = it % mapWidth
        val y = it / mapWidth
        val type = if (Random.nextInt(1, 11) == 10) BlockType.Gold else BlockType.Dirt
        Block(this, type, IntOffset(x - mapWidth / 2, y - mapHeight / 2))
    }.associateBy { it.pos }.toMutableMap()

    init {
        physics.system.earthGravityAcceleration = 20f

        player.render.positionState.change.listen {
            context.camera.setLookAt(it + Vec3f(0f, -8f, 0f), forward = Vec3f(0f, 1f, 0f))
            // This one is mostly for zooming in
            context.camera.focus(it, distance = 8f)
        }



        installMods(
            CreativeMovementMod(context, input),
            VisualEditorMod(hoverMod, input, enabled = false),
            RestartButtonsMod(context)
        )

    }
}


fun main() {
    startTheFun {
        { MineTheEarth(it) }
    }
}