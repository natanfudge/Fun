package io.github.natanfudge.fn.physics

import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.DurationUnit


//TODO: 1. outOfMemoryError
// 2. on-floor stuff is spazzing out
// velocity not updating in real time
/**
 * The physics system measures position in meters, velocity in meters per second, and acceleration in meters per second squared.
 * It is assumed the Z axis is down (sorry Minecraft bros).
 */
class PhysicsSystem(var gravity: Boolean = true) {
    companion object {
        const val EarthGravityAcceleration = 9.8f
    }


    private val bodies = mutableListOf<Body>()

    fun add(obj: Body) {
        bodies.add(obj)
    }

    fun remove(obj: Body) {
        bodies.remove(obj)
    }


    /**
     * Delta should be the fraction of the unit, so in this case in fractions of a second
     */
    fun tick(delta: Duration) {
        val groundedBodies = mutableSetOf<Body>()
        for ((a, b) in getIntersections()) {
            // Floor has special handling, we simply place the elements above the ground
            if (a.isFloor) {
                if (!b.isFloor) {
                    groundedBodies.add(b)
                    groundBody(floor = a, body = b)
                }

            } else if (b.isFloor) {
                if (!a.isFloor) {
                    groundedBodies.add(a)
                    groundBody(floor = b, body = a)
                }
            } else {
                applyElasticCollision(a, b)
            }
        }

        for (body in bodies) {
            if (gravity && body !in groundedBodies) applyGravity(body, delta)
            applyDisplacement(body, delta)
        }


    }

    /**
     * If a body touches the floor, we firmly place it above the floor and stop it from moving
     */
    private fun groundBody(floor: Body, body: Body) {
        body.velocity = body.velocity.copy(z = 0f)
        body.acceleration = body.acceleration.copy(z = 0f)

        // It's better for it to sink slightly into the ground so it will stay firmly in place
        body.position = body.position.copy(z = floor.boundingBox.maxZ + body.boundingBox.height / 2 - 0.0001f)
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
        if (body.affectedByGravity && !body.isFloor) {
            body.velocity = body.velocity.copy(
                z = body.velocity.z - EarthGravityAcceleration * delta.seconds.toFloat()
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
                if (bodyA.boundingBox.intersects(bodyB.boundingBox)) {
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

private operator fun Vec3f.times(ue: Double) = Vec3f((x * ue).toFloat(), (y * ue).toFloat(), (z * ue).toFloat())

private val Duration.seconds get() = toDouble(DurationUnit.SECONDS)