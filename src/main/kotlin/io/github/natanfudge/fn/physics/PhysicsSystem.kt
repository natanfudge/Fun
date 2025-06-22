package io.github.natanfudge.fn.physics

import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit


// SLOW: now that we have commit we can easily avoid allocations now I think
/**
 * The physics system measures position in meters, velocity in meters per second, and acceleration in meters per second squared.
 * It is assumed the Z axis is down (sorry Minecraft bros).
 */
class PhysicsSystem(var gravity: Boolean = true) {
    var earthGravityAcceleration = 9.8f

    companion object {
        private val maxDelta = 40.milliseconds
        private val maxSkipTicks = 50
    }


    private val bodies = mutableListOf<Body>()

    fun add(obj: Body) {
        bodies.add(obj)
    }

    fun remove(obj: Body) {
        bodies.remove(obj)
    }

//    fun getTouching(body: Body): List<Body> {
//        val intersections = bodies.filter { intersect(body, it) && it !== body }
//        return intersections
//    }

    private fun isGrounded(body: Body): Boolean {
        for (other in bodies) {
            if (other.isImmovable &&
                // Make sure to not allow "standing on ledges" inside nearby walls
                other.boundingBox.intersects(body.boundingBox, xEpsilon = 0f, yEpsilon = 0f)
            ) {
                return true
            }
        }
        return false
    }

    private fun intersect(bodyA: Body, bodyB: Body) = bodyA.boundingBox.intersects(bodyB.boundingBox, epsilon = 0f)

    fun tick(delta: Duration) {
        if (delta > maxDelta) {
            val ticks = ceil(delta / maxDelta).toInt()
            if (ticks > maxSkipTicks) {
                println("Warn: physics is lagging behind by more than $maxSkipTicks ticks ($maxSkipTicks)!  Only $maxSkipTicks ticks will be emulated.")
                repeat(maxSkipTicks) {
                    singleTick(maxDelta)
                }
            } else {
                println("Warn: physics is lagging behind, quickly emulating $ticks ticks. If the game is sped up you can ignore this.")
                repeat(ticks - 1) {
                    singleTick(maxDelta)
                }
                // Run the leftover delta
                singleTick(delta - (maxDelta * (ticks - 1)))
            }

        } else {
            singleTick(delta)
        }
    }


    /**
     * Delta should be the fraction of the unit, so in this case in fractions of a second
     */
    private fun singleTick(delta: Duration) {
        for (body in bodies) {
            if (!body.isImmovable) {
                if (gravity) applyGravity(body, delta)
                applyDisplacement(body, delta)
            }
        }
        val intersections = getIntersections()
        for ((a, b) in intersections) {
            // When a body encounters an immovable object, we "push out" the body from the immovable object.
            // We make sure to do this first, then we resolve whether we need to stop velocity as a result of hitting a floor/wall
            if (a.isImmovable) {
                if (!b.isImmovable) {
                    pushOut(surface = a, body = b)
                }

            } else if (b.isImmovable) {
                if (!a.isImmovable) {
                    pushOut(surface = b, body = a)
                }
            } else {
                applyElasticCollision(a, b)
            }
        }

        // Now that nothing is inside the wall/floor, we can check if we hit a wall or stand on a floor
        for ((a, b) in intersections) {
            // Floor has special handling, we simply place the elements above the ground
            if (a.isImmovable) {
                if (!b.isImmovable) {
                    stop(surface = a, body = b)
                }

            } else if (b.isImmovable) {
                if (!a.isImmovable) {
                    stop(surface = b, body = a)
                }
            }
        }
        bodies.forEach {
            it.isGrounded = isGrounded(it)
            it.commit()
        }
    }


    /**
     * If a body touches the floor / wall, we firmly place it above the floor / wall and stop it from moving
     */
    private fun pushOut(surface: Body, body: Body) {
        val overlapByAxis = (0..2).map {
            it to surface.boundingBox.overlap(body.boundingBox, it)
        }

        // Push the body apart along the axis with the smallest overlap, so it stops and doesn't sink into the surface.
        val pushoutAxis = overlapByAxis.minBy { it.second }.first

        if (body.boundingBox.min(pushoutAxis) >= surface.boundingBox.min(pushoutAxis)) {
            val sinkAmount = (surface.boundingBox.max(pushoutAxis) - body.boundingBox.min(pushoutAxis)).roundUpTo5DecimalPoints()

            // Place above surface
            body.position = body.position.copy(
                pushoutAxis, value = body.position[pushoutAxis] + sinkAmount
            )

        } else {
            val sinkAmount = (body.boundingBox.max(pushoutAxis) - surface.boundingBox.min(pushoutAxis)).roundUpTo5DecimalPoints()
            // Place below surface
            body.position = body.position.copy(
                pushoutAxis, value = body.position[pushoutAxis] - sinkAmount
            )
        }
    }

    /**
     * If a body is close to a surface, and will reasonably touch it, we do not allow it to move through the surface, in the axis of that surface.
     */
    private fun stop(surface: Body, body: Body) {
        val overlapByAxis = (0..2).map {
            it to surface.boundingBox.overlap(body.boundingBox, it)
        }

        // Push the body apart along the axis with the smallest overlap, so it stops.
        val pushoutAxis = overlapByAxis.minBy { it.second }.first

        val otherAxes = overlapByAxis.filter { it.first != pushoutAxis }
        // Only stop if the body is reasonably touching the surface in the OTHER axes
        if (otherAxes.all { it.second > 0.001f }) {
            stopInAxis(body, pushoutAxis)
        }
    }

    private fun stopInAxis(body: Body, axis: Int) {
        body.velocity = body.velocity.copy(axis = axis, value = 0f)
        body.acceleration = body.acceleration.copy(axis = axis, value = 0f)
    }

    private fun applyElasticCollision(a: Body, b: Body) {
        val direction = b.position - a.position
        val n = direction.normalize()
        val delta = (a.velocity - b.velocity).dot(n)
        val newV1 = a.velocity - n * ((2 * b.mass) / (a.mass + b.mass)) * delta
        val newV2 = b.velocity + n * ((2 * a.mass) / (a.mass + b.mass)) * delta
        a.velocity = newV1
        b.velocity = newV2
    }

    private fun applyDisplacement(body: Body, delta: Duration) {
        body.velocity += body.acceleration * delta.seconds
        body.position += body.velocity * delta.seconds
        body.rotation = updateRotation(body.rotation, body.angularVelocity, delta.seconds.toFloat())
    }

    private fun applyGravity(body: Body, delta: Duration) {
        if (body.affectedByGravity) {
            body.velocity = body.velocity.copy(
                z = body.velocity.z - earthGravityAcceleration * delta.seconds.toFloat()
            )
        }
    }

    private fun getIntersections(): List<Pair<Body, Body>> {
        val intersections = mutableListOf<Pair<Body, Body>>()
        // To avoid checking the same pair twice (A,B and B,A) and checking a body against itself (A,A),
        // we use nested loops where the inner loop starts from the element after the outer loop's current element.
        for (i in 0 until bodies.size - 1) {
            for (j in i + 1 until bodies.size) {
                val bodyA = bodies[i]
                val bodyB = bodies[j]

                // Use the Body's boundingBox property to check for intersection.
                if (intersect(bodyA, bodyB)) {
                    intersections.add(bodyA to bodyB)
                }
            }
        }
        return intersections
    }

    /**
     * Updates a rotation quaternion based on an angular velocity vector.
     * Mutates [currentRotation]
     */
    private fun updateRotation(currentRotation: Quatf, angularVelocity: Vec3f, deltaTime: Float): Quatf {
        // We'll work with a local copy
        var rotation = currentRotation

        // Calculate the magnitude of the angular velocity
        val angle = angularVelocity.length

        // A small epsilon to avoid issues with zero-length vectors
        if (angle > 0.0001) {
            // The total angle to rotate this frame is the angular speed (angle) * time
            // The quaternion formula uses half the angle.
            val halfAngle = angle * deltaTime * 0.5

            // The axis of rotation is the normalized angular velocity vector.
            // We can get it by dividing the vector by its length (angle).
            val axis = angularVelocity / angle

            // Create the delta rotation quaternion
            val sin_hA = sin(halfAngle).toFloat()
            val cos_hA = cos(halfAngle).toFloat()

            val deltaRotation = Quatf(
                w = cos_hA,
                x = axis.x * sin_hA,
                y = axis.y * sin_hA,
                z = axis.z * sin_hA
            )

            // Apply the delta rotation to the current rotation.
            // The order is important: delta * current applies rotation in world space.
            rotation = deltaRotation * rotation

            // Re-normalize the quaternion to prevent floating-point drift.
            // This is crucial for stability over time.
            rotation.normalize(currentRotation)
        }
        // If angularVelocity is zero, no change in rotation.
        return rotation
    }
}

fun Float.roundUpTo5DecimalPoints(): Float {
    val scale = 100_000f
    return ceil(this * scale) / scale
}



private operator fun Vec3f.times(ue: Double) = Vec3f((x * ue).toFloat(), (y * ue).toFloat(), (z * ue).toFloat())

private val Duration.seconds get() = toDouble(DurationUnit.SECONDS)