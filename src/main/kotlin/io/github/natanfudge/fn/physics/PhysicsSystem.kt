package io.github.natanfudge.fn.physics

import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.time.Duration
import kotlin.time.DurationUnit

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
        for (body in bodies) {
            if (gravity) applyGravity(body, delta)
            applyDisplacement(body, delta)
        }
        for(intersection in getIntersections()) {
        }
    }

    private fun applyDisplacement(body: Body, delta: Duration) {
        //TODO: we can avoid a lot of allocations here
        body.velocity += body.acceleration * delta.seconds
        body.position += body.velocity * delta.seconds
    }

    private fun applyGravity(body: Body, delta: Duration) {
        if (body.affectedByGravity) {
            body.acceleration = body.acceleration.copy(
                body.acceleration.x, body.acceleration.y, (body.acceleration.z - EarthGravityAcceleration * delta.seconds).toFloat(),
                dst = body.acceleration // Avoid allocation
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
}

private operator fun Vec3f.times(value: Double) = Vec3f((x * value).toFloat(), (y * value).toFloat(), (z * value).toFloat())

private val Duration.seconds get() = toDouble(DurationUnit.SECONDS)