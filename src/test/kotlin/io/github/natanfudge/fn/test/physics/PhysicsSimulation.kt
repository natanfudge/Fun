package io.github.natanfudge.fn.test.physics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.base.PhysicsMod
import io.github.natanfudge.fn.compose.utils.FunTheme
import io.github.natanfudge.fn.compose.utils.rememberPersistentFloat
import io.github.natanfudge.fn.core.ComposePanelPlacer
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.physics.PhysicsSystem
import io.github.natanfudge.fn.physics.SimplePhysicsObject
import io.github.natanfudge.fn.physics.SimpleRenderObject
import io.github.natanfudge.fn.render.DefaultCamera
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.Tint
import io.github.natanfudge.fn.util.toString
import kotlin.time.Duration

abstract class PhysicsTest(show: Boolean = false) {
    abstract fun PhysicsSimulationContext.run()

    init {
        runTest(show)
    }
}

private fun PhysicsTest.runTest(show: Boolean) {
    if (show) {
        startTheFun {
            {
                println("Physics app init")
                PhysicsSimulationApp(it, this@runTest)
            }
        }
    } else {
        val physics = PhysicsSystem()

        PhysicsSimulationContext(
            PhysicsSimulationFunContext(), scheduler = PhysicsSimulationScheduler(physics), physics = physics
        ).run()
    }
}


class PhysicsSimulationContext(context: FunContext, val physics: PhysicsSystem, val scheduler: Scheduler) : FunContext by context {
    private val cubeModel = Model(Mesh.UnitCube(), id = "mesh-0")
    private var index = 0
    fun cube() = SimplePhysicsObject("body-${index++}", this, cubeModel, physics)

    /**
     * Note that [callback] is called immediately, only the assertions occur in a delay.
     */
    fun after(delay: Duration, callback: PhysicsAssertionBlock.() -> Unit) {
        val block = PhysicsAssertionBlock().apply(callback)
        spawnTargetGhosts(block)
        placeCameraToShowEverything(block)

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
            SimpleRenderObject("target-${index++}", this, body.render.model).render.apply {
                position = assertion.position
                tint = Tint(Color.Red.copy(alpha = 0.5f))
                scale = body.render.scale * 1.1f
            }
        }
    }

    private fun placeCameraToShowEverything(block: PhysicsAssertionBlock) {
        camera.viewAll(
            positions = block.assertions.flatMap {
                listOf(
                    it.first.render.position,
                    it.second.position
                )
            },
            1f,
            fovYRadians = window.fovYRadians,
            aspectRatio = window.aspectRatio
        )
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

class VisibleSimulationTickerMod(val context: FunContext, val physics: PhysicsSystem) : Scheduler, FunMod {
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
    override fun physics(delta: Duration) {
        if (callback == null) return
        val newTime = timeSinceScheduleStart + delta
        if (newTime > scheduleDelay) {
            physics.tick(newTime - scheduleDelay)  // Only simulate a fraction of the delay, so that the we don't overshoot the amount of delta the physics system is supposed to process
            // After the final bit of physics is squeezed out, notify completion
            callback?.invoke()
            callback = null
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


class PhysicsSimulationApp(override val context: FunContext, simulation: PhysicsTest) : FunApp() {
    val physics = PhysicsMod()
    val scheduler = installMod(VisibleSimulationTickerMod(context, physics.system))

    init {
        val context = PhysicsSimulationContext(context, physics.system, scheduler)
        with(simulation) {
            context.run()
        }
    }

    @Composable
    override fun ComposePanelPlacer.gui() = FunTheme {
        Box(Modifier.fillMaxSize().background(Color.Transparent)) {
            Surface(Modifier.align(Alignment.CenterStart).padding(10.dp)) {
                Column(Modifier.padding(5.dp)) {
                    var stopped by remember { mutableStateOf(false) }
                    IconButton(onClick = {
                        if (stopped) {
                            context.time.resume()
                        } else {
                            context.time.stop()
                        }
                        stopped = !stopped
                    }) {
                        if (stopped) {
                            Icon(Icons.Filled.PlayArrow, "start")
                        } else {
                            Icon(Icons.Filled.Pause, "stop")
                        }
                    }
                    IconButton(onClick = { context.restartApp() }) {
                        Icon(Icons.Filled.Refresh, "restart")
                    }
                    var simulationSpeed by rememberPersistentFloat("simulation-speed") { 1f }
                    LaunchedEffect(simulationSpeed) {
                        context.time.speed = simulationSpeed
                    }
                    Text("x${simulationSpeed.toString(2)}", Modifier.align(Alignment.CenterHorizontally))
                    Row {
                        Slider(simulationSpeed, onValueChange = {
                            simulationSpeed = it
                        }, valueRange = 0.1f..10f, modifier = Modifier.width(200.dp))
                        IconButton(onClick = { simulationSpeed = 1f}) {
                            Icon(Icons.Filled.Refresh, "reset speed")
                        }
                    }

                }
            }
        }
    }
}