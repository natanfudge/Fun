package io.github.natanfudge.fn.test.physics

import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds


class TestPhysics {
    @Test
    fun testVelocityVisual() {
        // it might make more sense to add a specialized API for testing physics, this way we don't need to stub all the Fun context things.
        object : PhysicsTest(show = true) {
            override fun PhysicsSimulationContext.run() {
                val initialPosition = Vec3f.zero()
                val velocity = Vec3f(1f, 2f, 3f)

                val kinematic = cube()
                kinematic.physics.apply {
                    position = initialPosition.copy()
                    this.velocity = velocity
                    affectedByGravity = false
                }

                after(5.seconds) {
                    kinematic.shouldHave(
                        position = initialPosition + velocity * 5f
                    )
                }
            }
        }
    }

    @Test
    fun testSpin() {
        // it might make more sense to add a specialized API for testing physics, this way we don't need to stub all the Fun context things.
        object : PhysicsTest(show = true) {
            override fun PhysicsSimulationContext.run() {
                val initialPosition = Vec3f.zero()

                val kinematic = cube()
                kinematic.physics.apply {
                    position = initialPosition.copy()
                    angularVelocity = Vec3f(1f, 1f, 0f)
                    affectedByGravity = false
                }

                // No assertion for now
                after(20.seconds) {
                }
            }
        }
    }


    @Test
    fun testCollision() {
        // it might make more sense to add a specialized API for testing physics, this way we don't need to stub all the Fun context things.
        object : PhysicsTest(show = true) {
            override fun PhysicsSimulationContext.run() {
                val initialPosition = Vec3f.zero()

                val cube1 = cube()
                cube1.physics.apply {
                    position = initialPosition.copy(z = 10f)
                    affectedByGravity = true
//                    velocity = Vec3f(1f, 0f, 10f)
                    mass = 2f
                }

                val cube2 = cube()
                cube2.physics.apply {
                    position = initialPosition.copy(z = 0f)
                    affectedByGravity = false
                    mass = 99999999f
                }

                // No assertion for now
                after(100000.seconds) {
                    cube1.shouldHave(position = Vec3f(10f, 5f, 0.5f))
                    cube2.shouldHave(position = Vec3f(-10f, 5f, 0.5f))
                }
            }
        }
    }
}
