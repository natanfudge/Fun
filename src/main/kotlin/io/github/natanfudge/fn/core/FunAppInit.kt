@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.FunStateContext
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.util.ValueHolder
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.GlfwGameLoop
import io.github.natanfudge.fn.window.WindowConfig
import org.jetbrains.skiko.currentNanoTime
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWErrorCallback
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.TimeSource


val ProcessLifecycle = Lifecycle.create<Unit, Unit>("Process") {
    GLFWErrorCallback.createPrint(System.err).set()

    // Initialize GLFW. Most GLFW functions will not work before doing this.
    if (!glfwInit()) {
        throw IllegalStateException("Unable to initialize GLFW")
    }
    it
}


/**
 * How many times the app has been hot-reloaded
 */
var hotReloadIndex = 0


private fun run(app: FunAppInitializer<*>) {
    val window = app.init()
    val loop = GlfwGameLoop(window.window)

    ProcessLifecycle.start(Unit)

    FunHotReload.reloadStarted.listen {
        println("Reload started: pausing app")

        loop.locked = true
    }



    FunHotReload.reloadEnded.listen {
        // This has has special handling because it needs to run while the gameloop is locked
        loop.reloadCallback = {
            hotReloadIndex++
            println("Reloading app")

            ProcessLifecycle.removeChildren()

            app.close()
            app.init()

            try {
                ProcessLifecycle.restartByLabels(setOf(WebGPUWindow.SurfaceLifecycleLabel, ComposeWebGPURenderer.SurfaceLifecycleName))
            } catch (e: Throwable) {
                println("Failed to perform a granular restart, trying to restart the app entirely")
                e.printStackTrace()
                ProcessLifecycle.restart()
            }
            println("Reload19")

        }
    }
    loop.loop()
    exitProcess(0)
}

class FunTime(context: FunContext) : Fun("time", context) {
    var speed by funValue(1f, "speed")
    internal lateinit var app: FunApp
    fun advance(time: Duration) {
        app.actualPhysics(time)
    }

    var stopped by funValue(false, "stopped")

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

        app.actualPhysics(actualDelta)
    }

}

internal const val AppLifecycleName = "App"

private class FunAppInitializer<T : FunApp>(private val app: FunAppInit<T>) {

    private lateinit var fsWatcher: FileSystemWatcher
    fun init(): WebGPUWindow {
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

        val compose = ComposeWebGPURenderer(window, fsWatcher, show = false)
        val appLifecycle: Lifecycle<*, FunApp> = funSurface.bind(AppLifecycleName) {
            val context = FunContext(it, funDimLifecycle, compose, FunStateContext.isolatedClient())
            val time = FunTime(context)
            context.time = time

            val app = initFunc(context)
            time.app = app
            it.ctx.window.callbacks["Fun"] = FunInputAdapter(app)
            app
        }
        compose.compose.windowLifecycle.bind(appLifecycle, "App Compose binding") { comp, app ->
            comp.setContent {
                app.actualGui()
            }
        }

        window.bindFunLifecycles(compose, fsWatcher, appLifecycle, funSurface, funDimLifecycle)

        return window
    }

    fun close() {
        fsWatcher.close()
    }
}


abstract class FunApp : AutoCloseable {

    internal val mods = mutableListOf<FunMod>()

    fun <T : FunMod> installMod(mod: T): T {
        mods.add(mod)
        return mod
    }

    fun installMods(vararg mods: FunMod) {
        mods.forEach { installMod(it) }
    }


    @Composable
    open fun ComposePanelPlacer.gui() {

    }

    open fun handleInput(input: InputEvent) {

    }

    open fun frame(delta: Double) {

    }

    open fun physics(delta: Duration) {

    }

    abstract val context: FunContext

    open fun cleanup() {

    }

    final override fun close() {
        mods.forEach { it.cleanup() }
        cleanup()
//        context.close()
    }
}

@Composable
internal fun FunApp.actualGui() = context.panels.PanelSupport {
    gui()
    mods.forEach {
        with(it) {
            gui()
        }
    }
}

internal fun FunApp.actualHandleInput(input: InputEvent) {
    // No need to block input with a null cursor position
    if (context.world.cursorPosition != null && input is InputEvent.PointerEvent && !context.panels.acceptMouseEvents) return
    for (mod in mods) {
        mod.handleInput(input)
    }
    handleInput(input)
}


internal fun FunApp.actualFrame(deltaMs: Double) {
    mods.forEach { it.frame(deltaMs) }
    frame(deltaMs)
}

internal fun FunApp.actualPhysics(delta: Duration) {
    mods.forEach { it.prePhysics(delta) }
    physics(delta)
    mods.forEach { it.postPhysics(delta) }
}


class FunContext(
    private val surface: FunSurface, dims: ValueHolder<FunWindow>, private val compose: ComposeWebGPURenderer,
    private val stateContext: FunStateContext,
) : FunStateContext by stateContext {

    lateinit var time: FunTime


    val window by dims

    val world = surface.world

    var camera = DefaultCamera()

    val rootFuns = mutableMapOf<FunId, Fun>()
//    private var restarting = false

//    private val appRestarted = MutEventStream<Unit>()

    fun restartApp() {
//        restarting = true
        rootFuns.forEach { it.value.close(unregisterFromParent = false, unregisterFromContext = false)}

        ProcessLifecycle.restartByLabel(AppLifecycleName)
    }

    fun register(fn: Fun) {
        if (fn.isRoot) rootFuns[fn.id] = fn
        stateContext.stateManager.register(fn)
    }

    fun unregister(fn: Fun) {
        // We don't need to unregister anything because the entire context is getting thrown out, and this causes ConcurrentModificationException anyway
//        if (restarting) return
        if (fn.isRoot) rootFuns.remove(fn.id)
        stateContext.stateManager.unregister(fn)
    }

//    override fun onAppRestarted(callback: () -> Unit) {
//        super.onAppRestarted(callback)
//    }

    val panels: Panels = Panels()


    fun setCursorLocked(locked: Boolean) {
        surface.ctx.window.cursorLocked = locked
        if (locked) world.cursorPosition = (null)
    }

    fun setGUIFocused(focused: Boolean) {
        compose.compose.windowLifecycle.assertValue.focused = focused
    }

//    override fun close() {
//        appRestarted.clearListeners()
//    }
}


typealias FunAppInit<T> = FunAppBuilder.() -> ((context: FunContext) -> T)

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
fun <T : FunApp> startTheFun(init: FunAppInit<T>) {
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

