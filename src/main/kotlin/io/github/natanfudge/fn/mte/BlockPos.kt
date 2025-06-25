package io.github.natanfudge.fn.mte

import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.wgpu4k.matrix.Vec3f

data class BlockPos(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun squaredDistance(other: BlockPos): Int {
        val xDiff = x - other.x
        val yDiff = y - other.y
        val zDiff = z - other.z
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff
    }

    fun squaredDistance(vec: Vec3f): Float {
        val xDiff = x - vec.x
        val yDiff = y - vec.y
        val zDiff = z - vec.z
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff
    }

    fun to2D() = IntOffset(x, z)

    fun toVec3() = Vec3f(x + 0.5f, y + 0.5f, z + 0.5f)

}

/**
 * Will round down the xyz values to get the correct BlockPos
 */
fun Vec3f.toBlockPos() = BlockPos(x.toInt(), y.toInt(), z.toInt())
fun IntOffset.to2DBlockPos() = BlockPos(x, 0, y)

