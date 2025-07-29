package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.Camera
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan


/**
 * A 3D camera that handles positioning, orientation, and movement in a 3D space.
 * 
 * The camera maintains its position, orientation vectors (forward, up, right), and provides
 * methods for movement and rotation. It automatically calculates and updates its view matrix
 * which can be used in rendering pipelines.
 * 
 * The camera supports various movement modes:
 * - Standard movement (forward, backward, left, right, up, down)
 * - Tilting (changing orientation)
 * - Panning (moving perpendicular to the view direction)
 * - Zooming (moving closer to or further from a focal point)
 * - Rotation around axes
 */
class NewDefaultCamera: Camera, NewFun("Camera", Unit) {
    /**
     * The current position of the camera in 3D space.
     * Initially positioned at (5, 5, 5).
     */
     override val position by funValue(Vec3f(5f, 5f, 5f))

    /**
     * The up vector defining the camera's orientation.
     * Points along the positive Z axis (0, 0, 1) by default.
     */
    private val up by funValue(Vec3f(0f, 0f, 1f))

    /**
     * The direction the camera is looking.
     * Always normalized to unit length.
     * Initially points from the starting position toward the origin.
     */
    override val forward by funValue ((Vec3f.zero() - position).normalize())

    fun setLookAt(position: Vec3f, forward: Vec3f) {
        this.position.set(position)
        this.forward.set(forward)
        updateRight()
        calculateMatrix()
    }

    /**
     * The right vector, perpendicular to both [up] and [forward].
     * Always normalized to unit length.
     * Forms the X axis of the camera's local coordinate system.
     */
    val right = up.cross(forward)





    /**
     * Updates the [right] vector to maintain orthogonality with [up] and [forward].
     * Called whenever the camera orientation changes.
     */
    private fun updateRight() {
        up.cross(forward, right)
        right.normalize(right)
    }

    /**
     * The point that the camera focuses on when zooming.
     * Set to the origin (0,0,0) by default.
     */
    private val focalPoint = Vec3f.zero()

    /**
     * The view matrix representing the camera's transformation.
     * This matrix transforms points from world space to camera space.
     * Used in rendering pipelines, typically combined with a projection matrix.
     */
    override val viewMatrix = Mat4f.identity()

    init {
        calculateMatrix()
    }

    /**
     * Will place [focalPoint] at the center of the camera's view, standing at distance [distance] from it.
     */
    fun focus(focalPoint: Vec3f, distance: Float) {
        // Remember the focal point we are orbiting/zooming around
        this.focalPoint.set(focalPoint)

        // 1. Make the forward vector point at the focal point
        focalPoint.sub(position, forward)   // forward ← focal-point − position
        forward.normalize(forward)

        // 2. Move the camera so it sits `distance` units behind the focal point
        forward.mulScalar(-distance, tempVec)   // step back along −forward
        focalPoint.add(tempVec, position)       // position ← focal-point − forward*distance

        // 3. Keep the basis orthonormal and update the view matrix
        updateRight()
        calculateMatrix()
    }

    /**
     * Positions the camera in such a way that all [positions] are visible, including a [padding] margin around them.
     * This method calculates the bounding sphere of all points, then positions the camera at a sufficient
     * distance to frame this sphere based on the provided field of view and aspect ratio.
     *
     */
    fun viewAll(positions: List<Vec3f>, padding: Float, fovYRadians: Float, aspectRatio: Float) {
        if (positions.isEmpty()) {
            return // Nothing to view
        }

        // 1. Calculate the Axis-Aligned Bounding Box (AABB) enclosing all points.
        val aabbMin = positions[0].copy()
        val aabbMax = positions[0].copy()

        for (i in 1 until positions.size) {
            val pos = positions[i]
            aabbMin.x = min(aabbMin.x, pos.x)
            aabbMin.y = min(aabbMin.y, pos.y)
            aabbMin.z = min(aabbMin.z, pos.z)

            aabbMax.x = max(aabbMax.x, pos.x)
            aabbMax.y = max(aabbMax.y, pos.y)
            aabbMax.z = max(aabbMax.z, pos.z)
        }

        // 2. Determine the center and radius of the bounding sphere that encloses the AABB.
        val center = aabbMin.add(aabbMax).mulScalar(0.5f)
        val radius = aabbMax.sub(center).length + padding

        // Handle cases with no volume (e.g., all points are identical).
        if (radius <= 1e-6f) {
            // Focus on the point from a reasonable distance. Padding might be 0, so use a fallback.
            focus(center, (padding + 1.0f) * 2.0f)
            return
        }

        // 3. Calculate the required distance to fit the bounding sphere in the view frustum.
        // We use trigonometry, where tan(halfFov) = radius / distance.
        // The camera must be far enough away to fit the sphere both vertically and horizontally.

        // Tangent of the half vertical field of view.
        val tanHalfFovY = tan(fovYRadians / 2.0f)

        // The horizontal field of view is derived from the vertical FOV and aspect ratio.
        // tan(fovX / 2) = tan(fovY / 2) * aspectRatio
        val tanHalfFovX = tanHalfFovY * aspectRatio

        // Distance required to fit the sphere vertically: dist_v = radius / tan(fovY / 2)
        val distanceV = radius / tanHalfFovY

        // Distance required to fit the sphere horizontally: dist_h = radius / tan(fovX / 2)
        val distanceH = radius / tanHalfFovX

        // The actual distance must be the larger of the two to ensure the sphere is fully visible.
        val distance = max(distanceV, distanceH)

        // 4. Use the existing `focus` method to position and orient the camera.
        // This will aim the camera at the 'center' and move it back by 'distance'
        // along the current line of sight, preserving the viewing angle.
        focus(center, distance)
    }

    /**
     * Tilts the camera by adjusting its [forward] direction.
     * 
     * @param x Amount to tilt horizontally (positive values tilt right, negative left)
     * @param y Amount to tilt vertically (positive values tilt up, negative down)
     */
    fun tilt(x: Float, y: Float) {
        if (x != 0f) {
            forward.lerp(right, x, forward)
            forward.normalize(forward)
        }
        if (y != 0f) {
            forward.lerp(up, y, forward)
            forward.normalize(forward)
        }
        updateRight()

        calculateMatrix()
    }

    /**
     * Moves the camera forward along its current horizontal direction.
     * Movement is constrained to the horizontal plane (no vertical movement).
     * 
     * @param delta Distance to move (positive values move forward, negative backward)
     */
    fun moveForward(delta: Float) {
        moveLevel(forward, delta)
    }

    /**
     * Moves the camera left along its current horizontal right vector.
     * Movement is constrained to the horizontal plane (no vertical movement).
     * 
     * @param delta Distance to move (positive values move left, negative right)
     */
    fun moveLeft(delta: Float) {
        moveLevel(right, delta)
    }

    /**
     * Moves the camera up along the world up vector.
     * 
     * @param delta Distance to move (positive values move up, negative down)
     */
    fun moveUp(delta: Float) {
        position.add(up * delta, position)
        calculateMatrix()
    }

    /**
     * Pans the camera perpendicular to the view direction.
     * This moves the camera without changing its orientation.
     * 
     * @param x Amount to pan horizontally (positive values pan left, negative right)
     * @param y Amount to pan vertically (positive values pan up, negative down)
     */
    fun pan(x: Float, y: Float) {
        if (x != 0f) {
            move(right, -x)
        }
        if (y != 0f) {
            val orthonormalUp = right.cross(forward, tempVec)
            move(orthonormalUp, y)
            updateRight()
        }

        calculateMatrix()
    }

    /**
     * Zooms the camera toward or away from the [focalPoint].
     * 
     * @param multiplier Zoom factor (values > 1 zoom out, values < 1 zoom in)
     */
    fun zoom(multiplier: Float) {
        val focalPointDistance = position.sub(focalPoint, tempVec)
        focalPointDistance.mulScalar(multiplier, focalPointDistance)
        focalPoint.add(focalPointDistance, position)
        calculateMatrix()
    }


    /**
     * Rotates the camera around the Y axis (vertical rotation).
     * This method handles the special case when the camera is rotated so far that
     * the coordinate system would flip, by negating the up vector to maintain
     * consistent orientation.
     * 
     * @param radians Angle to rotate in radians (positive values rotate right, negative left)
     */
    fun rotateY(radians: Float) {
        val oldRight = right
        Mat4f.axisRotation(right, -radians, tempMatrix)

        val transformedForward = forward.transformMat4(tempMatrix, tempVec)
        val newRight = up.cross(transformedForward, tempVec)
        newRight.normalize(tempVec)
        if (!newRight.equalsApproximately(oldRight)) {
            // When the forward vector overtakes the up vector, the coordinate system becomes flipped, so to offset this we negate the up vector
            // This makes it appear as if nothing changed, but in fact the entire world has been turned upside down, which is probably the intention
            // If you rotated that much.
            // This was VERY DIFFICULT to get right.
            up.negate(up)
        }

        rotate(tempMatrix)
        calculateMatrix()
    }

    /**
     * Rotates the camera around the X axis (horizontal rotation).
     * 
     * @param radians Angle to rotate in radians (positive values rotate up, negative down)
     */
    fun rotateX(radians: Float) {
        Mat4f.axisRotation(up, radians, tempMatrix)
        rotate(tempMatrix)
        updateRight()
        calculateMatrix()
    }

    /**
     * Moves the camera down along the world up vector.
     * Convenience method that calls [moveUp] with a negative value.
     * 
     * @param delta Distance to move down
     */
    fun moveDown(delta: Float) = moveUp(-delta)

    /**
     * Moves the camera backward along its current horizontal direction.
     * Convenience method that calls [moveForward] with a negative value.
     * 
     * @param delta Distance to move backward
     */
    fun moveBackward(delta: Float) = moveForward(-delta)

    /**
     * Moves the camera right along its current horizontal right vector.
     * Convenience method that calls [moveLeft] with a negative value.
     * 
     * @param delta Distance to move right
     */
    fun moveRight(delta: Float) = moveLeft(-delta)

    /**
     * Temporary matrix used for calculations to avoid allocations.
     */
    private val tempMatrix = Mat4f()

    /**
     * Temporary vector used for calculations to avoid allocations.
     */
    private val tempVec = Vec3f()

    /**
     * Updates the view [viewMatrix] based on the current camera position and orientation.
     * Uses the lookAt function to create a view matrix that transforms points from
     * world space to camera space.
     */
    private fun calculateMatrix() {
        Mat4f.lookAt(
            eye = position,
            target = position + forward,
            up = up,
            dst = viewMatrix
        )
    }

    /**
     * Moves the camera along a horizontal plane in the given direction.
     * This ensures movement is constrained to the XY plane (no vertical/Z movement),
     * similar to how movement works in Minecraft.
     * 
     * @param direction The direction vector to move along
     * @param delta The distance to move
     */
    private fun moveLevel(direction: Vec3f, delta: Float) {
        tempVec.x = direction.x
        tempVec.y = direction.y
        tempVec.z = 0f
        tempVec.normalize(tempVec)
        tempVec.mulScalar(delta, tempVec)
        position.add(tempVec, position)
        // Don't move 'up' (z) with forward/back movement, Minecraft style
        calculateMatrix()
    }

    /**
     * Applies a rotation matrix to the camera's position and forward vector.
     * Used by the rotation methods to update the camera's orientation.
     * 
     * @param rotation The rotation matrix to apply
     */
    private fun rotate(rotation: Mat4f) {
        position.transformMat4(rotation, position)
        forward.transformMat4(rotation, forward)
        forward.normalize(forward)
    }

    /**
     * Moves the camera in the given direction by the specified distance.
     * Unlike [moveLevel], this allows movement in any direction including vertical.
     * 
     * @param direction The direction vector to move along
     * @param delta The distance to move
     */
    private fun move(direction: Vec3f, delta: Float) {
        val distance = direction.mulScalar(delta, tempVec)
        position.add(distance, position)
        calculateMatrix()
    }
}
