package io.github.natanfudge.fn.render

import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class Camera {
    private val position = Vec3f(5f, 5f, 5f)
    private val up = Vec3f(0f, 0f, 1f)

    /**
     * Always normalized
     */
    val forward = (Vec3f.zero() - position).normalize()

    private val focalPoint = Vec3f.zero()

    val matrix = Mat4f.identity()

    var orbital = false

    init {
        calculateMatrix()
    }

    private fun calculateMatrix() {
        Mat4f.lookAt(
            eye = position,
            target = position + forward,
            up = up,
            dst = matrix
        )
    }


    fun tilt(x: Float, y: Float) {
        if (x != 0f) {
            val target = up.cross(forward)
            target.normalize(target)
            forward.lerp(target, x, forward)
            forward.normalize(forward)
        }
        if (y != 0f) {
            forward.lerp(up, y, forward)
            forward.normalize(forward)
        }

        calculateMatrix()
    }

    fun moveForward(delta: Float) {
        moveLevel(forward, delta)
    }

    fun moveLeft(delta: Float) {
        moveLevel(up.cross(forward), delta)
    }

    fun moveUp(delta: Float) {
        position.add(up * delta, position)
        calculateMatrix()
    }


    private fun moveLevel(direction: Vec3f, delta: Float) {
        val onlyXY = Vec3f(direction.x, direction.y, 0f)
        onlyXY.normalize(onlyXY)
        onlyXY.mulScalar(delta, onlyXY)
        position.add(onlyXY, position)
        // Don't move 'up' (z) with forward/back movement, Minecraft style
        calculateMatrix()
    }

    fun pan(x: Float, y: Float) {
        val right = forward.cross(up).normalize()
        val up = forward.cross(right)

        if (x != 0f) {
            move(right, x)
        }
        if (y != 0f) {
            move(up, y)
        }

        calculateMatrix()
    }

    fun zoom(multiplier: Float) {
        val toFocal = position - focalPoint
        val newToFocal = toFocal * multiplier
        focalPoint.add(newToFocal, position)
        calculateMatrix()
    }


    fun rotateY(radians: Float) {
        val right = forward.cross(up).normalize()
        val rotation = Mat4f.axisRotation(right, radians)
        val transformedForward = forward.transformMat4(rotation)
        val newRight = transformedForward.cross(up).normalize()
        if (!newRight.equalsApproximately(right)) {
            // When the forward vector overtakes the up vector, the coordinate system becomes flipped, so to offset this we negate the up vector
            // This makes it appear as if nothing changed, but in fact the entire world has been turned upside down, which is probably the intention
            // If you rotated that much.
            // This was VERY DIFFICULT to get right.
            up.negate(up)
        }

        rotate(rotation)
    }

    fun rotateX(radians: Float) {
        val rotation = Mat4f.axisRotation(up, radians)
        rotate(rotation)
    }

    private fun rotate(rotation: Mat4f) {
        position.transformMat4(rotation, position)
        forward.transformMat4(rotation, forward)
        forward.normalize(forward)
        calculateMatrix()
    }

    private fun move(direction: Vec3f, delta: Float) {
        val diff = direction.normalize()
        diff.mulScalar(delta, diff)
        position.add(diff, position)
        calculateMatrix()
    }

    fun moveDown(delta: Float) = moveUp(-delta)
    fun moveBackward(delta: Float) = moveForward(-delta)
    fun moveRight(delta: Float) = moveLeft(-delta)

}