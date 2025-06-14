package io.github.natanfudge.fn.test.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunWorldRender
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.FunStateContext
import io.github.natanfudge.fn.physics.PhysicalFun
import io.github.natanfudge.fn.physics.PhysicsSystem
import io.github.natanfudge.fn.physics.Renderable
import io.github.natanfudge.fn.physics.SimpleKinematic
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPhysics {
    @Test
    fun testVelocity() {
        val initialPosition = Vec3f.zero()
        val velocity = Vec3f(1f, 2f, 3f)
        val kinematic = SimpleKinematic(
            initialPosition.copy(),
            velocity = velocity,
            boundingBox = AxisAlignedBoundingBox.UnitAABB,
        )
        val system = PhysicsSystem(gravity = false)
        system.add(kinematic)
        system.tick(5f)
        assertEquals(initialPosition + velocity * 5f, kinematic.position)
    }


    //TODO: See how we can integrate tick() into a visible physics test preview, and even stop and speed up the simulation.

    @Test
    fun testVelocityVisual() = physicsTest(show = true) {
        val initialPosition = Vec3f.zero()
        val velocity = Vec3f(1f, 2f, 3f)
        val kinematic = PhysicalFun(
            id = "body-0",
            context = this,
            model = Model(Mesh.UnitCube(), id = "mesh-0"),
            position = initialPosition.copy(),
            velocity = velocity
        )
        val system = PhysicsSystem(gravity = false)
        system.add(kinematic)
        system.tick(5f)
        assertEquals(initialPosition + velocity * 5f, kinematic.position)
        //TODO: just need to position the camera correctly now!
    }
}


fun physicsTest(show: Boolean = false, test: FunContext.() -> Unit) {
    if (show) {
        startTheFun {
            { PhysicsSimulationApp(it, test) }
        }
    } else {
        PhysicsSimulationFunContext().test()
    }
}

class PhysicsSimulationApp(context: FunContext, simulation: FunContext.() -> Unit) : FunApp {
    override val camera: Camera = DefaultCamera()

    init {
        simulation(context)
    }

}


class PhysicsSimulationFunContext() : FunContext, FunStateContext by FunStateContext.isolatedClient() {
    override val world: FunWorldRender = PhysicsSimulationFunWorld
    override val windowDimensions: IntSize = IntSize.Zero

    override fun setCursorLocked(locked: Boolean) {
    }

    override fun setGUIFocused(focused: Boolean) {
    }

}

object PhysicsSimulationFunWorld : FunWorldRender {
    override fun setCursorPosition(position: Offset?) {

    }

    override val hoveredObject: Renderable? = null

    override fun getOrBindModel(model: Model) = PhysicsSimulationModel

}

object PhysicsSimulationModel : BoundModel {
    override fun getOrSpawn(
        id: FunId,
        value: Renderable,
        tint: Tint,
    ) = PhysicsSimulationInstance

}

object PhysicsSimulationInstance : RenderInstance {
    override fun setTransform(transform: Mat4f) {
    }

    override fun setTintColor(color: Color) {
    }

    override fun setTintStrength(strength: Float) {
    }

    override fun despawn() {

    }
}


//TODO: make physics tests executable both in-memory and visually