package io.github.natanfudge.fn.test.physics

import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds


class TestPhysics {
    @Test
    fun testVelocityVisual() {
        // it might make more sense to add a specialized API for testing physics, this way we don't need to stub all the Fun context things.
        object : PhysicsTest(show = false) {
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
        object : PhysicsTest(show = false) {
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
        object : PhysicsTest(show = false, throwOnFailure = true) {
            override fun PhysicsSimulationContext.run() {
                physics.gravity = false
                val cube1 = cube()
                cube1.physics.apply {
                    position = Vec3f(x = 0f, y = 0f, z = 0f)
                    velocity = Vec3f(0f, 1f, 0f)
                    mass = 1f
                }

                val cube2 = cube()
                cube2.physics.apply {
                    position = Vec3f(x = 0f, y = 2f, z = 0f)
                    mass = 1f
                }

                after(5.seconds) {
                    cube1.shouldHave(position = Vec3f(x = 0f, y = 1f, z = 0f), epsilon = 0.1f)
                    cube2.shouldHave(position = Vec3f(0f, 6f, 0f), epsilon = 0.1f)
                }
            }
        }
    }

    @Test
    fun testFloor() {
        // it might make more sense to add a specialized API for testing physics, this way we don't need to stub all the Fun context things.
        object : PhysicsTest(show = false) {
            override fun PhysicsSimulationContext.run() {
                val cube = cube()
                cube.render.position = Vec3f(x = 0f, y = 0f, z = 5f)

                val floor = cube()
                floor.render.scale = Vec3f(10f, 10f, 0.1f)
                floor.physics.apply {
                    position = Vec3f.zero()
                    isImmovable = true
                }

                after(5.seconds) {
                    cube.shouldHave(position = Vec3f(x = 0f, y = 0f, z = 0.55f))
                    floor.shouldHave(position = Vec3f.zero())
                }
            }
        }
    }

    @Test
    fun testWall() {
        // it might make more sense to add a specialized API for testing physics, this way we don't need to stub all the Fun context things.
        object : PhysicsTest(show = true) {
            override fun PhysicsSimulationContext.run() {
                physics.gravity = false
                val cube = cube()
                cube.render.position = Vec3f(x = 0f, y = 0f, z = 0f)
                cube.physics.velocity = Vec3f(1f, 0.95f, 0f)

                val wall = cube()
                wall.physics.apply {
                    position = Vec3f(5f, 5f, 0f)
                    isImmovable = true
                }

                after(5.seconds) {
                    cube.shouldHave(position = Vec3f(x = 5f, y = 4f, z = 0f), epsilon = 0.01f)
                    wall.shouldHave(position = Vec3f(5f, 5f, 0f))
                }

            }
        }
    }

    @Test
    fun testSlide() {
        object : PhysicsTest(show = false) {
            override fun PhysicsSimulationContext.run() {
                val cube = cube()
                cube.render.position = Vec3f(x = 0f, y = 0f, z = 0.55f)
                cube.physics.velocity = Vec3f(1f, 0f, 0f)

                val floor = cube()
                floor.render.scale = Vec3f(10f, 10f, 0.1f)
                floor.physics.isImmovable = true

                after(5.seconds) {
                    cube.shouldHave(position = Vec3f(x = 5f, y = 0f, z = 0.55f))
                }
            }
        }
    }

    @Test
    fun testLedgeAbuse() {
        object : PhysicsTest(show = true) {
            override fun PhysicsSimulationContext.run() {
                val cube = cube()
                cube.render.position = Vec3f(x = 0f, y = 0f, z = 0.55f)
                cube.physics.velocity = Vec3f(1f, 0f, 0f)
            }
        }
    }
}
