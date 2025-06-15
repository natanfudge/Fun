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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

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
        physics.gravity = false

        val initialPosition = Vec3f.zero()
        val velocity = Vec3f(1f, 2f, 3f)
        val model = Model(Mesh.UnitCube(), id = "mesh-0")

        val expectedPosition = initialPosition + velocity * 5f




        val kinematic = PhysicalFun(
            id = "body-0",
            context = this@physicsTest,
            physics = physics,
            model = Model(Mesh.UnitCube(), id = "mesh-0"),
            position = initialPosition.copy(),
            velocity = velocity
        )
        PhysicalFun(
            id = "target",
            context = this@physicsTest,
            physics = physics,
            model = model,
            position = expectedPosition,
            tint = Tint(Color.Blue.copy(alpha = 0.5f)),
            scale = Vec3f(1.1f,1.1f,1.1f)
        )
        camera.focus(initialPosition, 30f)
        val startTime = System.nanoTime()
        println("Registered body")

        scheduler.schedule(5.seconds) { t ->
            val endTime = System.nanoTime()
            println("-------------------------")
            println("Passed time: ${(endTime - startTime) / 1e9}s")
            println("-------------------------")
            kinematic.position.shouldRoughlyEqual(initialPosition + velocity * t.seconds)
        }
    }
}

val Duration.seconds get() = this.toDouble(DurationUnit.SECONDS).toFloat()

class PhysicsSimulationContext(context: FunContext, val camera: DefaultCamera, val physics: PhysicsSystem, val scheduler: Scheduler) : FunContext by context

interface Scheduler {
    /**
     * Schedules [callback] to occur in approximately [delay].
     * Note that the exact delay won't be exactly [delay] because we only poll in discrete intervals, so the [delay] will be overshot by at least a little.
     * The exact amount of passed time will be passed to [callback].
     */
    fun schedule(delay: Duration, callback: (exactDelay: Duration) -> Unit)
}

class VisibleSimulationTickerMod(val context: FunContext) : Scheduler {
    private var callback: ((exactDelay: Duration) -> Unit)? = null
    private var remainingDuration = Duration.ZERO

    private var scheduleStartTime: TimeSource.Monotonic.ValueTimeMark? = null

    override fun schedule(delay: Duration, callback: (exactDelay: Duration) -> Unit) {
        context.time.resume() // Start simulation
        this.remainingDuration = delay
        this.callback = callback
        this.scheduleStartTime = TimeSource.Monotonic.markNow()
    }

    fun physics(delta: Float) {
        if (callback == null) return
        remainingDuration -= delta.toDouble().milliseconds
        if (remainingDuration <= Duration.ZERO) {
            callback?.invoke(scheduleStartTime!!.elapsedNow())
            callback = null
            context.time.stop() // Stop simulation
        }
    }
}

class PhysicsSimulationScheduler(val physics: PhysicsSystem) : Scheduler {
    override fun schedule(delay: Duration, callback: (exactDelay: Duration) -> Unit) {
        // Just run the physics right away
        physics.tick(delay.toDouble(DurationUnit.SECONDS).toFloat())
        callback(delay)
    }
}

fun physicsTest(show: Boolean = false, test: PhysicsSimulationContext.() -> Unit) {
    if (show) {
        startTheFun {
            { PhysicsSimulationApp(it, test) }
        }
    } else {
        val physics = PhysicsSystem()

        PhysicsSimulationContext(
            PhysicsSimulationFunContext(), DefaultCamera(), scheduler = PhysicsSimulationScheduler(physics), physics = physics
        ).test()
    }
}

var totalTimePassed = 0f

class PhysicsSimulationApp(override val context: FunContext, simulation: PhysicsSimulationContext.() -> Unit) : FunApp {
    override val camera = DefaultCamera()
    val physics = PhysicsMod()
    val scheduler = VisibleSimulationTickerMod(context)

    init {
        context.time.stop() // Avoid physics ticking before we initialized stuff
        simulation(PhysicsSimulationContext(context, camera, physics.system, scheduler))
    }

    override fun physics(delta: Float) {
        physics.physics(delta)
        println("Time passed: $delta, total: $totalTimePassed")
        totalTimePassed += delta
        scheduler.physics(delta)
    }

}

class PhysicsSimulationTime() : FunTime {
    override fun advance(time: java.time.Duration) {

    }

    override fun stop() {
    }

    override fun resume() {
    }

    override fun _poll() {

    }
}

class PhysicsSimulationFunContext() : FunContext, FunStateContext by FunStateContext.isolatedClient() {
    override val world: FunWorldRender = PhysicsSimulationFunWorld
    override val windowDimensions: IntSize = IntSize.Zero
    override val time: FunTime = PhysicsSimulationTime()
//    override val physics: PhysicsSystem = PhysicsSystem()

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