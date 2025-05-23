package io.github.natanfudge.fn.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec2f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.github.natanfudge.wgpu4k.matrix.Vec4f
import kotlin.math.max
import kotlin.math.min

data class Ray(
    val start: Vec3f,
    val direction: Vec3f,
//    val end: Vec3f,
) {
//    val direction = (end - start)
}

data class AABoundingBox(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
) {
    fun transformed(mat: Mat4f): AABoundingBox {
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

        return AABoundingBox(
            minX = nxMin, minY = nyMin, minZ = nzMin,
            maxX = nxMax, maxY = nyMax, maxZ = nzMax
        )
    }
}

fun getAxisAlignedBoundingBox(mesh: Mesh): AABoundingBox {
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
    return AABoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
}

interface Boundable {
    val boundingBox: AABoundingBox
}

//class RCObject<T: Boundable>(
//    val value: T
//    //  LATER: a proper containment test, that is more precise than the AABB check
//)

class RayCastingCache<T: Boundable> {
    private val items = mutableListOf<T>()

    fun add(item: T) {
        items.add(item)
    }

    fun remove(item: T) {
        items.remove(item)
    }

    fun rayCast(ray: Ray): T? {
        // SLOW: This check iterates over every mesh every frame, so ideally a better data structure is used
        // for ray-picking. I couldn't find any good library for it though.
        var closestDistance = Float.MAX_VALUE
        var closest: T? = null
        val currentDistanceVector = Vec2f()
        for (item in items) {
            if (intersectRayAab(ray.start, ray.direction, item.boundingBox, currentDistanceVector)) {
                // Check it's actually closer...
                if (currentDistanceVector.x < closestDistance) {

                    // LATER: Möller–Trumbore triaanglation intersection check
//                    val intersectionPoint = ray.start + (ray.direction * currentDistanceVector.x)
//                    if (mesh.containmentFunc.contains(intersectionPoint.toPoint3D())) {
                    closestDistance = currentDistanceVector.x
                    closest = item
//                    }
                }
            }
        }
        return closest
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
    private fun intersectRayAab(origin: Vec3f, dir: Vec3f, aabb: AABoundingBox, result: Vec2f): Boolean {
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

        return Ray(start, end - start)
    }
}