package io.github.natanfudge.fn.test.physics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.compose.utils.FunTheme
import io.github.natanfudge.fn.compose.utils.rememberPersistentFloat
import io.github.natanfudge.fn.core.ComposePanelPlacer
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.*
import io.github.natanfudge.fn.render.InputManagerMod
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.Tint
import io.github.natanfudge.fn.util.toFloat
import io.github.natanfudge.fn.util.toString
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.time.Duration
import kotlin.time.DurationUnit

class VisualPhysicsSimulation(val app: PhysicsSimulationApp) : PhysicsSimulation {

    override val physics: PhysicsSystem = app.physics.system
    private val cubeModel = Model(Mesh.HomogenousCube, id = "mesh-0")
    private var index = 0


    /**
     * Shows ghost variants of the asserted bodies at the expected final position of the bodies.
     */
    private fun spawnTargetGhosts(block: PhysicsAssertionBlock) {
        for ((body, assertion) in block.assertions) {
            val renderNode = (body as Fun).getRoot().childrenTyped<FunRenderState>().single()
            SimpleRenderObject("target-${index++}", app.context, renderNode.model).render.apply {
                position = assertion.position
                tint = Tint(Color.Red.copy(alpha = 0.5f))
                scale = renderNode.scale * 1.1f
            }
        }
    }


    private fun placeCameraToShowEverything(block: PhysicsAssertionBlock) {
        app.context.camera.viewAll(
            positions = block.assertions.flatMap {
                listOf(
                    it.first.position,
                    it.second.position
                )
            },
            1f,
            fovYRadians = app.context.window.fovYRadians,
            aspectRatio = app.context.window.aspectRatio
        )
    }

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
        val body = SimplePhysicsObject("body-${index++}", app.context, cubeModel, app.physics.system)
        body.render.scale = scale
        body.physics.position = position
        body.physics.rotation = rotation
        body.physics.velocity = velocity
        body.physics.mass = mass
        body.physics.affectedByGravity = affectedByGravity
        body.physics.angularVelocity = angularVelocity
        body.physics.isImmovable = isImmovable
        return body.physics
    }

    override fun after(delay: Duration, everyPhysicsTick: (Float) -> Unit, assertions: PhysicsAssertionBlock.() -> Unit) {
        val block = PhysicsAssertionBlock().apply(assertions)
        spawnTargetGhosts(block)
        placeCameraToShowEverything(block)

        app.scheduler.schedule(delay, everyPhysicsTick) {
            block.assertions.forEach {
                it.second.assert(it.first, app.throwOnFailure)
            }
        }
    }
}

class PhysicsSimulationApp(override val context: FunContext, simulation: PhysicsTest, val throwOnFailure: Boolean) : FunApp() {
    val physics = PhysicsMod()
    val scheduler = installMod(VisibleSimulationTickerMod(context, physics.system))

    val input = installMod(InputManagerMod())

    init {
        installMods(VisualEditorMod(this, input), CreativeMovementMod(context, installMod(input)))

        with(simulation) {
            VisualPhysicsSimulation(this@PhysicsSimulationApp).run()
        }
//        val context = PhysicsSimulationContext(context, physics.system, scheduler, throwOnFailure)
//        with(simulation) {
//            context.run()
//        }
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
                        IconButton(onClick = { simulationSpeed = 1f }) {
                            Icon(Icons.Filled.Refresh, "reset speed")
                        }
                    }

                }
            }
        }
    }
}

class VisibleSimulationTickerMod(val context: FunContext, val physics: PhysicsSystem) : FunMod {
    private var finishCallback: (() -> Unit)? = null
    private var tickCallback: ((Float) -> Unit)? = null

    private var timeSinceScheduleStart = Duration.ZERO
    private var scheduleDelay: Duration = Duration.ZERO

    fun schedule(delay: Duration, tickCallback: (Float) -> Unit, callback: () -> Unit) {
        this.scheduleDelay = delay
        this.finishCallback = callback
        this.tickCallback = tickCallback
    }


    /**
     * Returns the delta to be passed to the physics system.
     */
    override fun prePhysics(delta: Duration) {
        if (finishCallback == null) return
        val newTime = timeSinceScheduleStart + delta
        if (newTime > scheduleDelay) {
            val actualDelta = scheduleDelay - timeSinceScheduleStart
            tickCallback?.invoke(actualDelta.toFloat(DurationUnit.SECONDS))
            physics.tick(actualDelta)  // Only simulate a fraction of the delay, so that the we don't overshoot the amount of delta the physics system is supposed to process
            // After the final bit of physics is squeezed out, notify completion
            finishCallback?.invoke()
            finishCallback = null
        } else {
            timeSinceScheduleStart = newTime
            tickCallback?.invoke(delta.toFloat(DurationUnit.SECONDS))
            physics.tick(delta)
        }
    }
}

