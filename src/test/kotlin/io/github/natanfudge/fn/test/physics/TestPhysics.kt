package io.github.natanfudge.fn.test.physics

import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds


class TestPhysics {
    @Test
    fun testVelocityVisual() {
        // it might make more sense to add a specialized API for testing physics, this way we don't need to stub all the Fun context things.
        object : PhysicsTest(show = false) {
            override fun PhysicsSimulation.run() {
                val initialPosition = Vec3f.zero()
                val velocity = Vec3f(1f, 2f, 3f)

                val kinematic = cube(
                    position = initialPosition.copy(),
                    velocity = velocity,
                    affectedByGravity = false
                )

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
            override fun PhysicsSimulation.run() {
                val initialPosition = Vec3f.zero()

                val kinematic = cube(
                    position = initialPosition.copy(),
                    affectedByGravity = false,
                    angularVelocity = Vec3f(1f, 1f, 0f)
                )


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
            override fun PhysicsSimulation.run() {
                physics.gravity = false
                physics.elasticCollision = true
                val cube1 = cube(
                    position = Vec3f(x = 0f, y = 0f, z = 0f),
                    velocity = Vec3f(0f, 1f, 0f)
                )

                val cube2 = cube(
                    position = Vec3f(x = 0f, y = 2f, z = 0f)
                )

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
            override fun PhysicsSimulation.run() {
                val cube = cube(
                    position = Vec3f(x = 0f, y = 0f, z = 5f)
                )

                val floor = cube(
                    scale = Vec3f(10f, 10f, 0.1f),
                    position = Vec3f.zero(),
                    isImmovable = true
                )


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
        object : PhysicsTest(show = false) {
            override fun PhysicsSimulation.run() {
                physics.gravity = false
                val cube = cube(
                    position = Vec3f(x = 0f, y = 0f, z = 0f),
                    velocity = Vec3f(1f, 0.95f, 0f)
                )

                val wall = cube(
                    position = Vec3f(5f, 5f, 0f),
                    isImmovable = true
                )

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
            override fun PhysicsSimulation.run() {
                val cube = cube(
                    position = Vec3f(x = 0f, y = 0f, z = 0.55f),
                    velocity = Vec3f(1f, 0f, 0f)
                )


                val floor = cube(
                    scale = Vec3f(10f, 10f, 0.1f),
                    isImmovable = true
                )


                after(5.seconds) {
                    cube.shouldHave(position = Vec3f(x = 5f, y = 0f, z = 0.55f), epsilon = 0.01f)
                }
            }
        }
    }


    @Test
    @Ignore
    fun testLedgeAbuse() {
        object : PhysicsTest(show = false) {
            override fun PhysicsSimulation.run() {
                val cube = cube(
                    position = Vec3f(x = 0f, y = 0f, z = 4f),
                )
                val wall1 = cube(
                    position = Vec3f(x = 0f, y = 1f, z = 0f),
                    isImmovable = true
                )

                val wall2 = cube(
                    position = Vec3f(x = 0f, y = 1f, z = 1f),
                    isImmovable = true
                )
                val wall3 = cube(
                    position = Vec3f(x = 0f, y = 1f, z = 2f),
                    isImmovable = true
                )

                val floor = cube(position = Vec3f(x = 0f, y = 0f, z = -1f), scale = Vec3f(10f, 10f, 0.1f), isImmovable = true)

                var deltaSum = 0f

                after(5.seconds, everyPhysicsTick = {
                    deltaSum += it
                    if (deltaSum > 0.5f) {
                        cube.position += Vec3f(0f, it * 3, 0f)
                    }
                }) {
                    cube.shouldHave(position = Vec3f(x = 0f, y = 0f, z = -0.45f), velocity = Vec3f.zero())
                }

            }
        }
    }
}
