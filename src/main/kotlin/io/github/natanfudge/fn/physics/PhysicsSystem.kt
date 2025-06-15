package io.github.natanfudge.fn.physics

import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * The physics system measures position in meters, velocity in meters per second, and acceleration in meters per second squared.
 * It is assumed the Z axis is down (sorry Minecraft bros).
 */
class PhysicsSystem(var gravity: Boolean = true) {
    companion object {
        const val EarthGravityAcceleration = 9.8f
    }


    private val bodies = mutableListOf<Kinematic>()

    fun add(obj: Kinematic) {
        bodies.add(obj)
    }

    fun remove(obj: Kinematic) {
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
    }

    private fun applyDisplacement(body: Kinematic, delta: Duration) {
        //TODO: we can avoid a lot of allocations here
        body.velocity += body.acceleration * delta.seconds
        body.position += body.velocity * delta.seconds
    }

    private fun applyGravity(body: Kinematic, delta: Duration) {
        if (body.affectedByGravity) {
            body.acceleration = body.acceleration.copy(
                body.acceleration.x, body.acceleration.y, (body.acceleration.z - EarthGravityAcceleration * delta.seconds).toFloat(),
                dst = body.acceleration // Avoid allocation
            )
        }
    }
}

private operator fun Vec3f.times(value: Double) = Vec3f((x * value).toFloat(), (y * value).toFloat(), (z * value).toFloat())

private val Duration.seconds get() = toDouble(DurationUnit.SECONDS)