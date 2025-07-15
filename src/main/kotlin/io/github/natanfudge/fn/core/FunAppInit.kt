@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.FunInputAdapter
import io.github.natanfudge.fn.render.FunSurface
import io.github.natanfudge.fn.render.FunWindow
import io.github.natanfudge.fn.render.bindFunLifecycles
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.GlfwGameLoop
import io.github.natanfudge.fn.window.WindowConfig
import org.jetbrains.compose.reload.agent.invokeAfterHotReload
import org.jetbrains.skiko.currentNanoTime
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWErrorCallback
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime


val ProcessLifecycle = Lifecycle.create<Unit, Unit>("Process") {
    GLFWErrorCallback.createPrint(System.err).set()

    // Initialize GLFW. Most GLFW functions will not work before doing this.
    if (!glfwInit()) {
        throw IllegalStateException("Unable to initialize GLFW")
    }
    it
}




private fun run(app: FunAppInitializer) {
    val (initFunc, window) = app.init()
    val loop = GlfwGameLoop(window.window)

    ProcessLifecycle.start(Unit)

//    FunHotReload.reloadStarted.listen {
//        println("Reload started: pausing app")
//
//        loop.locked = true
//    }

    invokeAfterHotReload {id, result ->
        FunHotReload.reloadEnded.emit(result)
    }


    // Doing it this way allows us to emit it on our own terms
    FunHotReload.reloadEnded.listenUnscoped { result ->
//        val reload = result?.leftOrNull() ?: return@listen
//        println("Full reload information: " + reload)
//        println("redefined classes: ${reload.dirtyRuntime.redefinedClasses}")
//        println("")
//        reload.dirtyRuntime
//        reload.definitions.forEach {
//            println("Reloaded class: ${it.definitionClass}")
//        }
        loop.reloadCallback = {
            val time = measureTime {
                val context = FunContextRegistry.getContext()
                context.hotReloaded = true


                context.rootFuns.forEach {
                    it.value.close(
                        // Doesn't matter
                        unregisterFromParent = false,
                        // We want to preserver state
                        deleteState = false,
                        // The context is thrown out anyway, and this causes a CME on context.rootFuns
                        unregisterFromContext = false
                    )
                }

                context.clean()
                initFunc(context)
            }
            println("Reload done in $time")
        }
    }

//    staticHotReloadScope.invokeAfterHotReload {
//        loop.reloadCallback = {
//            hotReloadIndex++
//            println("Reloading app")
//
//            ProcessLifecycle.removeChildren()
//
//            app.close()
//            app.init()
//
//            try {
//                ProcessLifecycle.restartByLabels(setOf(WebGPUWindow.SurfaceLifecycleLabel, ComposeWebGPURenderer.SurfaceLifecycleName))
//            } catch (e: Throwable) {
//                println("Failed to perform a granular restart, trying to restart the app entirely")
//                e.printStackTrace()
//                ProcessLifecycle.restart()
//            }
//            println("Reload done2")
//
//        }
//    }

//    FunHotReload.reloadEnded.listen {
//        // This has has special handling because it needs to run while the gameloop is locked
//        loop.reloadCallback = {
//            hotReloadIndex++
//            println("Reloading app")
//
//            ProcessLifecycle.removeChildren()
//
//            app.close()
//            app.init()
//
//            try {
//                ProcessLifecycle.restartByLabels(setOf(WebGPUWindow.SurfaceLifecycleLabel, ComposeWebGPURenderer.SurfaceLifecycleName))
//            } catch (e: Throwable) {
//                println("Failed to perform a granular restart, trying to restart the app entirely")
//                e.printStackTrace()
//                ProcessLifecycle.restart()
//            }
//            println("Reload19")
//
//        }
//    }
    loop.loop()
    exitProcess(0)
}

class FunTime : Fun("time") {
    var speed by funValue(1f, "speed")
    internal lateinit var _context: FunContext
    fun advance(time: Duration) {
        _context.progressPhysics(time)
    }

    val stoppedState = funValue(false, "stopped")
    var stopped by stoppedState

    fun stop() {
        stopped = true
    }

    fun resume() {
        stopped = false
        prevPhysicsTime = TimeSource.Monotonic.markNow()
    }

    var gameTime: Duration by funValue(Duration.ZERO, "gameTime")

    private var prevPhysicsTime = TimeSource.Monotonic.markNow()

    fun _poll() {
        if (stopped) return

        val physicsDelta = prevPhysicsTime.elapsedNow()
        prevPhysicsTime = TimeSource.Monotonic.markNow()
        val actualDelta = physicsDelta * speed.toDouble()
        gameTime += actualDelta

        _context.progressPhysics(actualDelta)
    }

}

internal const val AppLifecycleName = "App"

private class FunAppInitializer(private val app: FunAppInit) {

    private lateinit var fsWatcher: FileSystemWatcher

    fun init(): Pair<(FunContext) -> Unit, WebGPUWindow> {
        val builder = FunAppBuilder()
        val initFunc = app(builder)
        val window = WebGPUWindow(builder.config)

        val funSurface = window.surfaceLifecycle.bind("Fun Surface") { surface ->
            FunSurface(surface)
        }

        val funDimLifecycle = window.dimensionsLifecycle.bind("Fun Dimensions") {
            FunWindow(it.surface, it.dimensions)
        }

        fsWatcher = FileSystemWatcher()

        var appLifecycle: Lifecycle<*, FunContext>? = null

        val compose = ComposeWebGPURenderer(window, fsWatcher, show = false, onError = {
            appLifecycle?.value?.events?.guiError?.emit(it)
        })
        appLifecycle = funSurface.bind(AppLifecycleName) {
            val context = FunContext(it, funDimLifecycle, compose, FunStateContext.isolatedClient())
            val time = FunTime()
            context.time = time

            initFunc(context)
            time._context = context
            it.ctx.window.callbacks["Fun"] = FunInputAdapter(context)
            context // Close context when lifecycle is restarted
        }
        compose.compose.windowLifecycle.bind(appLifecycle, "App Compose binding") { comp, context ->
            comp.setContent {
                context.gui.PanelSupport()
            }
        }

        window.bindFunLifecycles(compose, fsWatcher, appLifecycle, funSurface, funDimLifecycle)

        return initFunc to window
    }

    fun close() {
        fsWatcher.close()
    }
}


abstract class FunApp : AutoCloseable {


//    fun onComposeE

    abstract val context: FunContext


    final override fun close() {
        context.events.appClose.emit(Unit)
    }
}






internal fun FunContext.progressPhysics(delta: Duration) {
    events.beforePhysics.emit(delta)
    events.physics.emit(delta)
    events.afterPhysics.emit(delta)
}


typealias FunAppInit = FunAppBuilder.() -> ((context: FunContext) -> Unit)

class FunAppBuilder {
    internal var config: WindowConfig = WindowConfig()
    fun window(config: WindowConfig) {
        this.config = config
    }
}

/**
 * Start a Fun app.
 * This accepts a lambda for configuring the app (` FunAppBuilder.() -> `), which returns a lambda that creates the app (`((context: FunContext) -> T)`).
 * The reason [init] does not simply return a FunApp, is because the outer lambda is called whenever the window is created, and the inner lambda
 * is called whenever the app is created, allowing for more granular hot reloading.
 */
fun startTheFun(init: FunAppInit) {
    run(FunAppInitializer(init))
}


sealed interface InputEvent {
    object WindowClosePressed : InputEvent
    data class PointerEvent(
        val eventType: PointerEventType,
        val position: Offset,
        val scrollDelta: Offset = Offset.Zero,
        val timeMillis: Long = currentTimeForEvent(),
        val type: PointerType = PointerType.Mouse,
        val buttons: PointerButtons? = null,
        val keyboardModifiers: PointerKeyboardModifiers? = null,
        val nativeEvent: Any? = null,
        val button: PointerButton? = null,
    ) : InputEvent

    data class KeyEvent(val event: androidx.compose.ui.input.key.KeyEvent) : InputEvent
    data class WindowMove(val offset: IntOffset) : InputEvent
}


private fun currentTimeForEvent(): Long = (currentNanoTime() / 1E6).toLong()

const val HOT_RELOAD_SHADERS = true

