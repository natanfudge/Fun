package io.github.natanfudge.fn.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec2f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.github.natanfudge.wgpu4k.matrix.Vec4f
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Ray(
    val start: Vec3f,
    val direction: Vec3f,
) {
}

@Serializable
data class AxisAlignedBoundingBox(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
) {

    fun min(axis: Int) = when (axis) {
        0 -> minX
        1 -> minY
        2 -> minZ
        else -> throw IllegalArgumentException("Invalid axis $axis")
    }

    fun max(axis: Int) = when (axis) {
        0 -> maxX
        1 -> maxY
        2 -> maxZ
        else -> throw IllegalArgumentException("Invalid axis $axis")
    }

    /**
     * Returns the signed overlap of two AABBs on the requested axis (0=x, 1=y, 2=z).
     * A positive result means the boxes intersect by that amount.
     * A zero or negative result means no overlap on that axis.
     */
    fun overlap(other: AxisAlignedBoundingBox, axis: Int): Float {
        return min(this.max(axis), other.max(axis)) - max(this.min(axis), other.min(axis))
    }

    val width get() = maxX - minX
    val depth get() = maxY - minY
    val height get() = maxZ - minZ
    fun getCenter() = Vec3f(
        (maxX + minX) / 2,
        (maxY + minY) / 2,
        (maxZ + minZ) / 2
    )
    fun size(axis: Int) = when (axis) {
        0 -> width
        1 -> depth
        2 -> height
        else -> throw IllegalArgumentException("Invalid axis $axis")
    }

    companion object {
        val UnitAABB = AxisAlignedBoundingBox(
            -0.5f, -0.5f, -0.5f,
            0.5f, 0.5f, 0.5f,
        )

        fun cube(center: Vec3f, height: Float): AxisAlignedBoundingBox {
            return AxisAlignedBoundingBox(
                center.x - height / 2f,
                center.y - height / 2f,
                center.z - height / 2f,
                center.x + height / 2f,
                center.y + height / 2f,
                center.z + height / 2f,
            )
        }
    }

    fun intersects(other: AxisAlignedBoundingBox, epsilon: Float = 1e-5f): Boolean = intersects(other, epsilon, epsilon, epsilon)
    fun intersects(other: AxisAlignedBoundingBox, xEpsilon: Float = 1e-5f, yEpsilon: Float = 1e-5f, zEpsilon: Float = 1e-5f): Boolean {
        return (minX - xEpsilon <= other.maxX && maxX + xEpsilon >= other.minX) &&
                (minY - yEpsilon <= other.maxY && maxY + yEpsilon >= other.minY) &&
                (minZ - zEpsilon <= other.maxZ && maxZ + zEpsilon >= other.minZ)
    }

    fun intersects(other: AxisAlignedBoundingBox): Boolean {
        return (minX <= other.maxX && maxX >= other.minX) &&
                (minY <= other.maxY && maxY >= other.minY) &&
                (minZ <= other.maxZ && maxZ >= other.minZ)
    }

    fun transformed(mat: Mat4f): AxisAlignedBoundingBox {
        // helper to track the new extrema
        var nxMin = Float.POSITIVE_INFINITY
        var nyMin = Float.POSITIVE_INFINITY
        var nzMin = Float.POSITIVE_INFINITY
        var nxMax = Float.NEGATIVE_INFINITY
        var nyMax = Float.NEGATIVE_INFINITY
        var nzMax = Float.NEGATIVE_INFINITY

        // all 8 corner combinations
        val xs = floatArrayOf(minX, maxX)
        val ys = floatArrayOf(minY, maxY)
        val zs = floatArrayOf(minZ, maxZ)

        for (x in xs) {
            for (y in ys) {
                for (z in zs) {
                    // transform the point (x,y,z,1)
                    val tx = mat.m00 * x + mat.m01 * y + mat.m02 * z + mat.m03
                    val ty = mat.m10 * x + mat.m11 * y + mat.m12 * z + mat.m13
                    val tz = mat.m20 * x + mat.m21 * y + mat.m22 * z + mat.m23
                    val tw = mat.m30 * x + mat.m31 * y + mat.m32 * z + mat.m33

                    // if it's a projective transform, divide by w
                    val invW = if (tw != 0f) 1f / tw else 1f
                    val px = tx * invW
                    val py = ty * invW
                    val pz = tz * invW

                    // update mins
                    if (px < nxMin) nxMin = px
                    if (py < nyMin) nyMin = py
                    if (pz < nzMin) nzMin = pz

                    // update maxs
                    if (px > nxMax) nxMax = px
                    if (py > nyMax) nyMax = py
                    if (pz > nzMax) nzMax = pz
                }
            }
        }

        return AxisAlignedBoundingBox(
            minX = nxMin, minY = nyMin, minZ = nzMin,
            maxX = nxMax, maxY = nyMax, maxZ = nzMax
        )
    }
}

fun getAxisAlignedBoundingBox(mesh: Mesh): AxisAlignedBoundingBox {
    require(mesh.indices.size != 0)
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var minZ = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    var maxZ = Float.MIN_VALUE

    mesh.forEachPos {
        minX = min(minX, it.x)
        minY = min(minY, it.y)
        minZ = min(minZ, it.z)
        maxX = max(maxX, it.x)
        maxY = max(maxY, it.y)
        maxZ = max(maxZ, it.z)
    }
    return AxisAlignedBoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
}

interface Boundable {
    val boundingBox: AxisAlignedBoundingBox
//    val data: Any?
}

//class RCObject<T: Boundable>(
//    val value: T
//    //  LATER: a proper containment test, that is more precise than the AABB check
//)

data class RayCastResult<T>(
    val obj: T,
    val pos: Vec3f,
)

class RayCastingCache<T : Boundable> {
    private val items = mutableListOf<T>()

    fun add(item: T) {
        items.add(item)
    }

    fun remove(item: T) {
        items.remove(item)
    }

    /**
     * Returns the object intersecting with the ray [ray] and where it intersected with it.
     */
    fun rayCast(ray: Ray): RayCastResult<T>? {
        // SLOW: This check iterates over every mesh every frame, so ideally a better data structure is used
        // for ray-picking. I couldn't find any good library for it though.
        var closestDistance = Float.MAX_VALUE
        var closest: T? = null
        val currentDistanceVector = Vec2f()
        for (item in items) {
            if (intersectRayAab(ray.start, ray.direction, item.boundingBox, currentDistanceVector)) {
                // LATER: Möller–Trumbore triaanglation intersection check
//                    val intersectionPoint = ray.start + (ray.direction * currentDistanceVector.x)
//                    if (mesh.containmentFunc.contains(intersectionPoint.toPoint3D())) {

                // Check it's actually closer...
                if (currentDistanceVector.x < closestDistance) {
                    closestDistance = currentDistanceVector.x
                    closest = item
                }
            }
        }
        return if (closest != null && closestDistance.isFinite()) {
            val intersectionPoint = ray.start + (ray.direction * closestDistance)
            RayCastResult(closest, intersectionPoint)
        } else null
    }

    /**
     * Test whether the given ray with the origin `(originX, originY, originZ)` and direction `(dirX, dirY, dirZ)`
     * intersects the axis-aligned box given as its minimum corner `(minX, minY, minZ)` and maximum corner `(maxX, maxY, maxZ)`,
     * and return the values of the parameter *t* in the ray equation *p(t) = origin + t * dir* of the near and far point of intersection.
     *
     *
     * This method returns `true` for a ray whose origin lies inside the axis-aligned box.
     *
     *
     * If many boxes need to be tested against the same ray, then the [RayAabIntersection] class is likely more efficient.
     *
     *
     * Reference: [An Efficient and Robust Ray–Box Intersection](https://dl.acm.org/citation.cfm?id=1198748)
     * @param result a vector which will hold the resulting values of the parameter
     *      *              <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the near and far point of intersection
     *      *              iff the ray intersects the axis-aligned box
     */
    private fun intersectRayAab(origin: Vec3f, dir: Vec3f, aabb: AxisAlignedBoundingBox, result: Vec2f): Boolean {
        val minX = aabb.minX
        val maxX = aabb.maxX
        val minY = aabb.minY
        val maxY = aabb.maxY
        val minZ = aabb.minZ
        val maxZ = aabb.maxZ
        val originX = origin.x
        val originY = origin.y
        val originZ = origin.z

        val invDirX = 1.0f / dir.x
        val invDirY = 1.0f / dir.y
        val invDirZ = 1.0f / dir.z
        var tNear: Float
        var tFar: Float
        val tymin: Float
        val tymax: Float
        val tzmin: Float
        val tzmax: Float
        if (invDirX >= 0.0f) {
            tNear = (minX - originX) * invDirX
            tFar = (maxX - originX) * invDirX
        } else {
            tNear = (maxX - originX) * invDirX
            tFar = (minX - originX) * invDirX
        }
        if (invDirY >= 0.0f) {
            tymin = (minY - originY) * invDirY
            tymax = (maxY - originY) * invDirY
        } else {
            tymin = (maxY - originY) * invDirY
            tymax = (minY - originY) * invDirY
        }
        if (tNear > tymax || tymin > tFar) return false
        if (invDirZ >= 0.0f) {
            tzmin = (minZ - originZ) * invDirZ
            tzmax = (maxZ - originZ) * invDirZ
        } else {
            tzmin = (maxZ - originZ) * invDirZ
            tzmax = (minZ - originZ) * invDirZ
        }
        if (tNear > tzmax || tzmin > tFar) return false
        tNear = if (tymin > tNear || java.lang.Float.isNaN(tNear)) tymin else tNear
        tFar = if (tymax < tFar || java.lang.Float.isNaN(tFar)) tymax else tFar
        tNear = if (tzmin > tNear) tzmin else tNear
        tFar = if (tzmax < tFar) tzmax else tFar
        if (tNear < tFar && tFar >= 0.0f) {
            result.x = tNear
            result.y = tFar
            return true
        }
        return false
    }

}

object RayCastUtil {
    fun rayPlaneIntersection(
        ray: Ray, planeNormal: Vec3f, planePos: Vec3f, epsilon: Float = 1e-6f,
    ): Vec3f? {
        val r0 = ray.start
        val rd = ray.direction

        val denom = rd.dot(planeNormal)

        // Parallel (or nearly): either no hit or coplanar
        if (abs(denom) < epsilon) {
            return null
        }

        val s = (planePos - r0).dot(planeNormal) / denom

        if (s < 0f) return null
        if (!s.isFinite()) return null

        return r0 + rd * s
    }

    /**
     * Computes a stable work-plane normal for axis-constrained dragging.
     *
     * The plane is defined to:
     *  - pass through the gizmo origin (not needed here, only the normal is returned), and
     *  - contain the drag axis `axis`.
     *
     * We build the normal as n = normalize( (b × axis) ), where `b` is a vector
     * chosen to be as close as possible to the camera's view direction while
     * remaining non-parallel to `axis`. Concretely:
     *
     *   1) Try base = axis × camDir.
     *   2) If |base| is very small (axis ~ parallel to camera), fall back to
     *      base = axis × up.
     *   3) The final normal is normalize(base × axis).
     *
     * This ensures the work plane contains `axis` and stays well-conditioned
     * for mouse ray intersections in perspective cameras.
     *
     * @param epsilon Threshold below which two vectors are considered nearly parallel.
     * @return A unit-length normal vector for the work plane.
     */
    fun workPlaneNormal(
        /**
         * Must be a unit vector
         */
        axis: Vec3f,
        camera: DefaultCamera,
        epsilon: Float = 1e-4f,
    ): Vec3f {
        // Primary base: perpendicular to both axis and camera direction
        var base = axis.cross(camera.forward)
        if (base.lengthSquared() < epsilon) {
            // Fallback if axis ~ parallel to camera: use world up instead
            base = axis.cross(camera.up)
            // If still degenerate (e.g., up also ~ parallel), perturb with a different hint
            if (base.lengthSquared() < epsilon) {
                // Create a tiny orthogonal nudge to break degeneracy
                val tweak = if (abs(axis.y) < 0.9) Vec3f(0f, 1f, 0f) else Vec3f(1f, 0f, 0f)
                base = axis.cross(tweak)

                if (base.lengthSquared() < epsilon) {
                    // Final fallback: arbitrary orthogonal via permutation (guaranteed non-zero for unit a)
                    base = axis.cross(
                        if (abs(axis.x) <= abs(axis.y) && abs(axis.x) <= abs(axis.z)) Vec3f(1f, 0f, 0f)
                        else if (abs(axis.y) <= abs(axis.z)) Vec3f(0f, 1f, 0f)
                        else Vec3f(0f, 0f, 1f)
                    )
                }
            }
        }

        // The plane normal is perpendicular to both base and axis, and lies in the span {axis, camDir/up}
        val n = base.cross(axis).normalized()
        return n
    }

    /**
     * Returns the ray that is pointed by the cursor in the orbital view, in world space.
     */
    fun mouseRay(
        cursorCoords: Offset, // e.g. (mouseX, mouseY)
        screenSize: IntSize,   // e.g. (width, height)
        viewProjectionMatrix: Mat4f,
    ): Ray {

        // 1) Convert cursor from [0..width]x[0..height] to Normalized Device Coordinates [-1..+1].
        val xNdc = 2f * (cursorCoords.x / screenSize.width) - 1f
        // If your screen's y=0 is at TOP, then NDC y=+1 is at TOP, so invert:
        val yNdc = 1f - 2f * (cursorCoords.y / screenSize.height)

        // 2) Define near and far points in NDC space, in homogeneous coords (x, y, z, w=1).
        val nearNdc = Vec4f(xNdc, yNdc, -1f, 1f)
        val farNdc = Vec4f(xNdc, yNdc, +1f, 1f)

        // 3) Invert (Projection * View) matrix to go from NDC back to World space.
        val invProjView = viewProjectionMatrix.invert()

        // 4) Transform NDC points into World space.
        val nearWorld = invProjView * nearNdc
        val farWorld = invProjView * farNdc

        // 5) Perspective divide (x/w, y/w, z/w) - FIXED: Use nearWorld.w and farWorld.w
        val nearWorldDivided = Vec3f(nearWorld.x / nearWorld.w, nearWorld.y / nearWorld.w, nearWorld.z / nearWorld.w)
        val farWorldDivided = Vec3f(farWorld.x / farWorld.w, farWorld.y / farWorld.w, farWorld.z / farWorld.w)

        // 6) Extract world-space start/end as Vector3f
        val start = nearWorldDivided
        val end = farWorldDivided
        return Ray(start, end - start)
    }
}