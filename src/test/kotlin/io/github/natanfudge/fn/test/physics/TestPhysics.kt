package io.github.natanfudge.fn.test.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.PhysicsMod
import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.FunStateContext
import io.github.natanfudge.fn.physics.PhysicalFun
import io.github.natanfudge.fn.physics.PhysicsSystem
import io.github.natanfudge.fn.physics.Renderable
import io.github.natanfudge.fn.physics.SimpleKinematic
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.test.util.shouldRoughlyEqual
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

//TODO: must do before eitam:
// 1. DefaultCamera.viewAll
// 2. Allow stopping/resuming simulation
// 3. Allow speeding up / slowing down simulation
// 4. Implement collision detection first
// 5. Verify it works on laptop

class TestPhysics {
    @Test
    fun testVelocityVisual() = physicsTest(show = true) {
        val initialPosition = Vec3f.zero()
        val velocity = Vec3f(1f, 2f, 3f)

        val kinematic = cube().apply {
            position = initialPosition.copy()
            this.velocity = velocity
            affectedByGravity = false
        }

        // TODO: replace this with an automatic camera.showAll call
        camera.focus(initialPosition, 30f)


        after(5.seconds) {
            kinematic.shouldHave(
                position = initialPosition + velocity * 5f
            )
        }
    }
}
