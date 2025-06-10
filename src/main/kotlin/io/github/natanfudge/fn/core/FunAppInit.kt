@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.render.BoundModel
import io.github.natanfudge.fn.render.Camera
import io.github.natanfudge.fn.render.FunFixedSizeWindow
import io.github.natanfudge.fn.render.FunSurface
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.RenderInstance
import io.github.natanfudge.fn.render.WorldRender
import io.github.natanfudge.fn.render.bindFunLifecycles
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.util.ValueHolder
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.GlfwGameLoop
import io.github.natanfudge.fn.window.WindowConfig
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import org.jetbrains.skiko.currentNanoTime
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWErrorCallback
import kotlin.concurrent.atomics.ExperimentalAtomicApi


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
                ProcessLifecycle.restartByLabels(setOf(WebGPUWindow.SurfaceLifecycleLabel))
            } catch (e: Throwable) {
                println("Failed to perform a granular restart, trying to restart the app entirely")
                e.printStackTrace()
                ProcessLifecycle.restart()
            }
            println("Reload19")

        }
    }
    loop.loop()
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

        // This a hack to get the FunApp to run after all the other things. A better solution is pending.
        val hack1 = ProcessLifecycle.bind("hack1") {}
        val hack2 = hack1.bind("hack2"){}
        val hack3 = hack2.bind("hack3"){}


        fsWatcher = FileSystemWatcher()

        val compose = ComposeWebGPURenderer(window, fsWatcher, show = false)
        val appLifecycle: Lifecycle<*, FunApp> = hack3.bind("App") {
            initFunc(FunContext(funSurface, funDimLifecycle, compose))
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

    fun renderInit(world: WorldRender) {

    }

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



abstract class RenderBoundPhysical(val render: WorldRender, )

//abstract class BasePhysical(override var transform: Mat4f, private var baseAABB: Mat4f): Physical {
//    override var baseAABB: Mat4f
//        get() = TODO("Not yet implemented")
//        set(value) {}
//}


class FunContext(surface: ValueHolder<FunSurface>, dims: ValueHolder<FunFixedSizeWindow>, private val compose: ComposeWebGPURenderer) {
    private val dims by dims
    private val surface by surface

    private val world get() = surface.world

    val windowWidth get() = dims.dims.width
    val windowHeight get() = dims.dims.height

    val selectedObject: Physical get() = world.selectedObject

    fun setCursorLocked(locked: Boolean) {
        surface.ctx.window.cursorLocked = locked
    }

    fun setGUIFocused(focused: Boolean) {
        compose.compose.windowLifecycle.assertValue.focused = focused
    }

    fun spawn(model: BoundModel, transform: Mat4f = Mat4f.identity(), color: Color = Color.White): RenderInstance {
        return world.spawn(model, transform, color)
    }

    fun bind(model: Model): BoundModel {
        return world.bind(model)
    }


}


typealias FunAppInit<T> = FunAppBuilder.() -> ((context: FunContext) -> T)

class FunAppBuilder {
    internal var config: WindowConfig = WindowConfig()
    fun window(config: WindowConfig) {
        this.config = config
    }
}

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

