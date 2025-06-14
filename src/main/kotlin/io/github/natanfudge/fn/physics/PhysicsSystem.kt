package io.github.natanfudge.fn.physics

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
    fun tick(deltaSeconds: Float) {
        for (body in bodies) {
            if (gravity) applyGravity(body, deltaSeconds)
            applyDisplacement(body, deltaSeconds)
        }
    }

    private fun applyDisplacement(body: Kinematic, delta: Float) {
        //TODO: we can avoid a lot of allocations here
        body.velocity += body.acceleration * delta
        body.position += body.velocity * delta
    }

    private fun applyGravity(body: Kinematic, delta: Float) {
        body.acceleration = body.acceleration.copy(
            body.acceleration.x, body.acceleration.y, body.acceleration.z + EarthGravityAcceleration * delta,
            dst = body.acceleration // Avoid allocation
        )
    }
}