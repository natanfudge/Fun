@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.network.FunStateContext
import io.github.natanfudge.fn.physics.Renderable
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


private fun run(app: BaseFunApp<*>) {
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

private class BaseFunApp<T : FunApp>(private val app: FunAppInit<T>) {
    private lateinit var fsWatcher: FileSystemWatcher
    fun init(): WebGPUWindow {
        val builder = FunAppBuilder()
        val initFunc = app(builder)
        val window = WebGPUWindow(builder.config)

        val funSurface = window.surfaceLifecycle.bind("Fun Surface") { surface ->
            FunSurface(surface)
        }

        val funDimLifecycle = window.dimensionsLifecycle.bind("Fun Dimensions") {
            FunFixedSizeWindow(it.surface, it.dimensions)
        }

        fsWatcher = FileSystemWatcher()

        val compose = ComposeWebGPURenderer(window, fsWatcher, show = false)
        val appLifecycle: Lifecycle<*, FunApp> = funSurface.bind("App") {
            val app = initFunc(VisibleFunContext(it, funDimLifecycle, compose, FunStateContext.isolatedClient()))
            it.ctx.window.callbacks["Fun"] = FunInputAdapter(app)
            app
        }
        compose.compose.windowLifecycle.bind(appLifecycle, "App Compose binding") { comp, app ->
            comp.setContent {
                app.gui()
            }
        }

        window.bindFunLifecycles(compose, fsWatcher, appLifecycle, funSurface, funDimLifecycle)

        return window
    }

    fun close() {
        fsWatcher.close()
    }
}


interface FunApp : AutoCloseable {

    @Composable
    fun gui() {

    }

    fun handleInput(input: InputEvent) {

    }

    //TODO: think of how to do component-callback binding

    fun frame(delta: Float) {

    }

    fun physics(delta: Float) {

    }

    val camera: Camera

    override fun close() {

    }
}


abstract class RenderBoundPhysical(val render: WorldRender)

//abstract class BasePhysical(override var transform: Mat4f, private var baseAABB: Mat4f): Physical {
//    override var baseAABB: Mat4f
//        get() = TODO("Not yet implemented")
//        set(value) {}
//}

interface FunContext : FunStateContext {
    val world: FunWorldRender
    val windowDimensions: IntSize
    fun setCursorLocked(locked: Boolean)
    fun setGUIFocused(focused: Boolean)
}

interface FunWorldRender {
    /**
     * Used for ray casting, as the renderer is involved in it
     */
    fun setCursorPosition(position: Offset?)
    val hoveredObject: Renderable?

    /**
     * Used to access the BoundModel, only creating it when needing
     */
    fun getOrBindModel(model: Model): BoundModel
}

class VisibleFunContext(
    private val surface: FunSurface, dims: ValueHolder<FunFixedSizeWindow>, private val compose: ComposeWebGPURenderer,
    private val stateContext: FunStateContext,
) : FunContext, FunStateContext by stateContext {
    private val dims by dims

    override val windowDimensions: IntSize
        get() = IntSize(dims.dims.width, dims.dims.height)

    override val world = surface.world


    override fun setCursorLocked(locked: Boolean) {
        surface.ctx.window.cursorLocked = locked
        if (locked) world._cursorPosition = null
    }

    override fun setGUIFocused(focused: Boolean) {
        compose.compose.windowLifecycle.assertValue.focused = focused
    }
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
    run(BaseFunApp(init))
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

