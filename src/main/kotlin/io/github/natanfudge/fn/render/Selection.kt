package io.github.natanfudge.fn.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.github.natanfudge.wgpu4k.matrix.Vec4f
import kotlin.math.max
import kotlin.math.min

data class Ray(
    val start: Vec3f,
    val end: Vec3f,
) {
    val direction = (end - start)
}

data class AABoundingBox(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
) {

    fun getOrigin(): Point3D = Point3D(minX, minY, minZ)

    fun width() = maxX - minX
    fun depth() = maxY - minY
    fun height() = maxZ - minZ
}

fun getAxisAlignedBoundingBox(points: List<Vertex>): AABoundingBox {
    require(points.isNotEmpty())
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var minZ = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    var maxZ = Float.MIN_VALUE

    for (point in points) {
        minX = min(minX, point.pos.x)
        minY = min(minY, point.pos.y)
        minZ = min(minZ, point.pos.z)
        maxX = max(maxX, point.pos.x)
        maxY = max(maxY, point.pos.y)
        maxZ = max(maxZ, point.pos.z)
    }
    return AABoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
}

class RCObject<T>(
    val boundingBox:
    val value: T
)

class RayCastingCache {

}

object Selection {

    /**
     * Returns the ray that is represented by the cursor pointing at a specific point in the screen [cursorCoords] with size [screenSize], while standing
     * at the [position], looking at [target] where [up] is up, assuming all objects are transformed by the [viewMatrix] and [projectionMatrix].
     */
    private fun orbitalSelectionRay(
        cursorCoords: Offset, // e.g. (mouseX, mouseY)
        screenSize: IntSize,   // e.g. (width, height)
        projectionMatrix: Mat4f,
        viewMatrix: Mat4f,
    ): Ray {

        // 1) Convert cursor from [0..width]x[0..height] to Normalized Device Coordinates [-1..+1].
        val xNdc = 2f * (cursorCoords.x / screenSize.width) - 1f
        // If your screen's y=0 is at TOP, then NDC y=+1 is at TOP, so invert:
        val yNdc = 1f - 2f * (cursorCoords.y / screenSize.height)

        // 2) Define near and far points in NDC space, in homogeneous coords (x, y, z, w=1).
        val nearNdc = Vec4f(xNdc, yNdc, -1f, 1f)
        val farNdc = Vec4f(xNdc, yNdc, +1f, 1f)

        // 3) Invert (Projection * View) matrix to go from NDC back to World space.
        val invProjView = projectionMatrix.mul(viewMatrix).invert()

        // 4) Transform NDC points into World space.
        val nearWorld = invProjView * nearNdc
        val farWorld = invProjView * farNdc

        // 5) Perspective divide (x/w, y/w, z/w).
        nearWorld.div(nearNdc.w, nearWorld)
        farWorld.div(farNdc.w, nearWorld)

        // 6) Extract world-space start/end as Vector3f
        val start = Vec3f(nearNdc.x, nearNdc.y, nearNdc.z)
        val end = Vec3f(farNdc.x, farNdc.y, farNdc.z)

        return Ray(start, end)
    }
}