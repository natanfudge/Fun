package io.github.natanfudge.fn.test.physics

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.PhysicsMod
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.physics.PhysicalFun
import io.github.natanfudge.fn.physics.PhysicsSystem
import io.github.natanfudge.fn.render.DefaultCamera
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.Tint
import kotlin.time.Duration

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


class PhysicsSimulationContext(context: FunContext, val camera: DefaultCamera, val physics: PhysicsSystem, val scheduler: Scheduler) : FunContext by context {
    private val cubeModel = Model(Mesh.UnitCube(), id = "mesh-0")
    private var index = 0
    fun cube() = PhysicalFun("body-${index++}", this, physics, cubeModel)

    /**
     * Note that [callback] is called immediately, only the assertions occur in a delay.
     */
    fun after(delay: Duration, callback: PhysicsAssertionBlock.() -> Unit) {
        val block = PhysicsAssertionBlock().apply(callback)
        spawnTargetGhosts(block)

        scheduler.schedule(delay) {
            block.assertions.forEach {
                it.second.assert(it.first)
            }
        }
    }

    /**
     * Shows ghost variants of the asserted bodies at the expected final position of the bodies.
     */
    private fun spawnTargetGhosts(block: PhysicsAssertionBlock) {
        for ((body, assertion) in block.assertions) {
            PhysicalFun("target-${index++}",this, physics, body.model).apply {
                position = assertion.position
                tint = Tint(Color.Red.copy(alpha = 0.5f))
                scale = body.scale * 1.1f
                affectedByGravity = false
            }
        }
    }
}

interface Scheduler {
    /**
     * Schedules [callback] to occur in approximately [delay].
     * Note that the exact delay won't be exactly [delay] because we only poll in discrete intervals, so the [delay] will be overshot by at least a little.
     * The exact amount of passed time will be passed to [callback].
     */
    fun schedule(delay: Duration, callback: () -> Unit)
}

class VisibleSimulationTickerMod(val context: FunContext, val physics: PhysicsSystem) : Scheduler {
    private var callback: (() -> Unit)? = null

    private var timeSinceScheduleStart = Duration.ZERO
    private var scheduleDelay: Duration = Duration.ZERO

    override fun schedule(delay: Duration, callback: () -> Unit) {
        this.scheduleDelay = delay
        this.callback = callback
    }


    /**
     * Returns the delta to be passed to the physics system.
     */
    fun physics(delta: Duration) {
        if (callback == null) return
        val newTime = timeSinceScheduleStart + delta
        if (newTime > scheduleDelay) {
            callback = null
            physics.tick(newTime - scheduleDelay)  // Only simulate a fraction of the delay, so that the we don't overshoot the amount of delta the physics system is supposed to process
            // After the final bit of physics is squeezed out, notify completion
            callback?.invoke()
        } else {
            timeSinceScheduleStart = newTime
            physics.tick(delta)
        }
    }
}

class PhysicsSimulationScheduler(val physics: PhysicsSystem) : Scheduler {
    override fun schedule(delay: Duration, callback: () -> Unit) {
        // Just run the physics right away
        physics.tick(delay)
        callback()
    }
}




class PhysicsSimulationApp(override val context: FunContext, simulation: PhysicsSimulationContext.() -> Unit) : FunApp {
    override val camera = DefaultCamera()
    val physics = PhysicsMod()
    val scheduler = VisibleSimulationTickerMod(context, physics.system)

    init {
        simulation(PhysicsSimulationContext(context, camera, physics.system, scheduler))
    }

    override fun physics(delta: Duration) {
        scheduler.physics(delta)
    }

}