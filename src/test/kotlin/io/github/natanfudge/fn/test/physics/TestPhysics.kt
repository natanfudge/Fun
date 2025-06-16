package io.github.natanfudge.fn.test.physics

import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

//TODO: must do before eitam:
// 4. Implement collision detection first
// 5. Verify it works on laptop

class TestPhysics {
    @Test
    fun testVelocityVisual() {
        // it might make more sense to add a specialized API for testing physics, this way we don't need to stub all the Fun context things.
        object : PhysicsTest(show = true) {
            override fun PhysicsSimulationContext.run() {
                val initialPosition = Vec3f.zero()
                val velocity = Vec3f(1f, 2f, 3f)

                val kinematic = cube().apply {
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
}
