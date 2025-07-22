package io.github.natanfudge.fn.test.physics

import io.github.natanfudge.fn.core.SimpleLogger
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.physics.Body
import io.github.natanfudge.fn.physics.PhysicsSystem
import io.github.natanfudge.fn.physics.SimpleBody
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.milliseconds
import kotlin.time.Duration
import kotlin.time.DurationUnit

interface PhysicsSimulation {
    val physics: PhysicsSystem
    fun cube(
        position: Vec3f = Vec3f.zero(), rotation: Quatf = Quatf.identity(),
        scale: Vec3f = Vec3f(1f, 1f, 1f), velocity: Vec3f = Vec3f.zero(),
        mass: Float = 1f, affectedByGravity: Boolean = true,
        angularVelocity: Vec3f = Vec3f.zero(),
        isImmovable: Boolean = false,
    ): Body

    fun after(delay: Duration, everyPhysicsTick: (delta: Float) -> Unit = {}, assertions: PhysicsAssertionBlock.() -> Unit)
}

/**
 * We make the tests be anonymous classes because it makes hot reloading work better. We don't get methodNotFound errors.
 */
abstract class PhysicsTest(show: Boolean = false, throwOnFailure: Boolean = !show) {
    abstract fun PhysicsSimulation.run()

    init {
        runTest(show, throwOnFailure)
    }
}

private fun PhysicsTest.runTest(show: Boolean, throwOnFailure: Boolean) {
    if (show) {
        startTheFun {
            {
                PhysicsSimulationApp(it, this@runTest, throwOnFailure)
            }
        }
    } else {
        HeadlessPhysicsSimulation().run()
    }
}


class HeadlessPhysicsSimulation : PhysicsSimulation {
    override val physics = PhysicsSystem(SimpleLogger())
    override fun cube(
        position: Vec3f,
        rotation: Quatf,
        scale: Vec3f,
        velocity: Vec3f,
        mass: Float,
        affectedByGravity: Boolean,
        angularVelocity: Vec3f,
        isImmovable: Boolean,
    ): Body {
        val body = SimpleBody(
            position = position,
            orientation = rotation,
            scale = scale,
            velocity = velocity,
            mass = mass,
            affectedByGravity = affectedByGravity,
            angularVelocity = angularVelocity,
            isImmovable = isImmovable
        )
        physics.add(body)
        return body
    }

    override fun after(delay: Duration, everyPhysicsTick: (Float) -> Unit, assertions: PhysicsAssertionBlock.() -> Unit) {
        var timePassed = Duration.ZERO
        while (timePassed < delay) {
            val delta: Duration = 10.milliseconds
            timePassed += delta
            everyPhysicsTick(delta.toDouble(DurationUnit.SECONDS).toFloat())
            // Tick granularily to allow for collision, and to better match real world physics
            physics.tick(delta)
        }
        // Tick the remainder of the last tick
        val lastDelta = delay - timePassed
        everyPhysicsTick(lastDelta.toDouble(DurationUnit.SECONDS).toFloat())
        physics.tick(lastDelta)
        PhysicsAssertionBlock().apply(assertions).assertions.forEach {
            it.second.assert(it.first, throwOnFailure = true)
        }
    }
}