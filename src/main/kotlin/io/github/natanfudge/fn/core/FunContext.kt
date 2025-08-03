package io.github.natanfudge.fn.core

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.natanfudge.fn.compose.ComposeHudWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.util.filterIsInstance
import io.github.natanfudge.fn.webgpu.WebGPUSurfaceHolder
import io.github.natanfudge.fn.window.GlfwWindowHolder
import io.github.natanfudge.fn.window.GlfwWindowProvider
import io.github.natanfudge.fn.window.WindowConfig
import korlibs.time.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.reload.agent.Reload
import org.jetbrains.compose.reload.agent.invokeAfterHotReload
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.mapLeft
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.TimeSource

// 3. Setup webgpu world renderer
// 3.5 Cleanup NewGlfwWindow
// 4. refresh app on resize


/**
 * Note: this class is mounted directly on the FunContext which means its not reconstructed, although we could just have made it a normal component
 * and persisted the state lists.
 */
class FunEvents : Fun("FunEvents") {
    // see https://github.com/natanfudge/MineTheEarth/issues/115
    val beforeFrame by event<Duration>()
    val frame by event<Duration>()
    val afterFrame by event<Duration>()
    val beforePhysics by event<Duration>()
    val physics by event<Duration>()
    val afterPhysics by event<Duration>()
    val input by event<WindowEvent>()
    val guiError by event<Throwable>()
    val appClosed by event<Unit>()
    val hotReload by event<Reload>()
    val closeButtonPressed = input.filterIsInstance<WindowEvent.CloseButtonPressed>()
    val pointer = input.filterIsInstance<WindowEvent.PointerEvent>()
    val key = input.filterIsInstance<WindowEvent.KeyEvent>()
    val windowMove = input.filterIsInstance<WindowEvent.WindowMove>()
    val windowResize = input.filterIsInstance<WindowEvent.WindowResize>()
    val afterWindowResize by event<IntSize>()
    val densityChange = input.filterIsInstance<WindowEvent.DensityChange>()
    val windowClose = input.filterIsInstance<WindowEvent.WindowClose>()
}


private val maxFrameDelta = 300.milliseconds


//TODO
// 4. Resolve TODOs
// 5. Clean up


class FunContext(val appCallback: () -> Unit) : FunStateContext {
    init {
        FunContextRegistry.setContext(this)
    }

    val cache = FunCache()
    override val stateManager = FunStateManager()

    val rootFun = RootFun()

    val events = FunEvents()


    val fsWatcher = FileSystemWatcher()

    val logger = FunLogger()

    val time = FunTime()

    lateinit var gui: FunPanels

    // TODO: service registration so we don't need this
    lateinit var world: WorldRenderer
        internal set

    val camera get() = world.camera

    lateinit var compose: ComposeHudWebGPURenderer
        internal set


    internal fun register(fn: Fun) {
        stateManager.register(fn.id, allowReregister = true)
    }

    internal fun unregister(fn: Fun) {
        stateManager.unregister(fn.id)
    }


    private var previousFrameTime = TimeSource.Monotonic.markNow()

    private var pendingReload: Reload? = null

    fun setCursorLocked(locked: Boolean) {
        world.surfaceHolder.windowHolder.cursorLocked = locked
        if (locked) world.cursorPosition = (null)
    }

    fun setGUIFocused(focused: Boolean) {
        compose.offscreenComposeRenderer.scene.focused = focused
    }

    internal fun start() {
        invokeAfterHotReload { _, result ->
            println("Hot Reloading app with classes: ${result.leftOrNull()?.definitions?.map { it.definitionClass.simpleName }}")
            result.mapLeft {
                // This runs on a different thread which will cause issues, so we store it and will run it on the main thread later
                pendingReload = it
            }
        }

        events.hotReload.listenUnscoped {
            reload(it)
        }

        events.closeButtonPressed.listenUnscoped {
            exitProcess(0)
        }

        // Window resizing stalls the main loop, so we want to interject frames when resizing so the content adapts to the resize as soon as the user
        // does it.
        events.afterWindowResize.listenUnscoped {
            frame()
        }


        appCallback()
        // Nothing to close, but its fine, it will start everything without closing anything
//        initializer.finishRefresh()

        loop()
    }

    internal fun physics(delta: Duration) {
        events.beforePhysics(delta)
        events.physics(delta)
        events.afterPhysics(delta)
    }

    private fun frame() {
        val elapsed = previousFrameTime.elapsedNow()
        previousFrameTime = TimeSource.Monotonic.markNow()
        val delta = elapsed.coerceAtMost(maxFrameDelta)

        fsWatcher.poll()
        time.advance(delta)

        events.beforeFrame(delta)
        events.frame(delta)
        events.afterFrame(delta)

        //SUS: it's likely this is too late, and we need to run this pending reload check more often, since e.g. a refresh
        // might happen after beforePhysics and then physics will have old state.
        // But tbh this is an uphill battle, it would be best to interrupt the main thread on reload and do what we need.
        if (pendingReload != null) {
            // Run reload on main thread
            events.hotReload(pendingReload!!)
            pendingReload = null
        }
    }


    private fun loop() {
        while (true) {
            frame()
        }
    }


    private fun reload(reload: Reload) {
//        aggregateDirtyClasses(reload)
        events.appClosed(Unit)
        cache.prepareForRefresh(reload.definitions.map { it.definitionClass.kotlin })
        rootFun.close(unregisterFromParent = false, deleteState = false)
        rootFun.clearChildren()
        appCallback()
    }
}


private fun aggregateDirtyClasses(reload: Reload) {
    val classes = mutableSetOf<String>()
    for (scope in reload.dirty.dirtyScopes) {
        classes.add(scope.methodId.classId.value)
    }
    println(classes)
}


internal object FunContextRegistry {
    private lateinit var context: FunContext
    fun setContext(context: FunContext) {
        this.context = context
    }

    fun getContext() = context
}

// TODO: I think this could just be a component you use, but I should add a way to globally discover it , prob would be good to add
// a service locator, bound to the context.
// A dsl like this would make sense
// funApp(config = {
//      renderer = Renderer(800,600)
//      hoverService = null
// }) {
//}
//
class FunRenderer(config: WindowConfig) : Fun("FunBaseApp") {
    @Suppress("unused")
    val glfwInit by cached(InvalidationKey.None) {
        GlfwWindowProvider.initialize()
    }


    val windowHolder = GlfwWindowHolder(withOpenGL = false, showWindow = true, config, name = "WebGPU") {
        events.input(it)
        if (it is WindowEvent.WindowResize) {
            events.afterWindowResize(it.size)
        }
    }

    init {
        // Update CHR with the window state
        events.windowResize.listen { (newSize) ->
            if (newSize.isEmpty) {
                OrchestrationMessage.ApplicationWindowGone(WindowId(windowHolder.handle.toString())).sendAsync()
            }
            if (!newSize.isEmpty) {
                notifyCHROfWindowPosition()
            }
        }

        events.windowMove.listen { (offset) ->
            notifyCHROfWindowPosition()
        }
        events.windowClose.listen {
            OrchestrationMessage.ApplicationWindowGone(WindowId(windowHolder.handle.toString())).sendAsync()
        }
    }

    private fun notifyCHROfWindowPosition() {
        val pos = windowHolder.windowPos
        val size = windowHolder.size
        OrchestrationMessage.ApplicationWindowPositioned(
            WindowId(windowHolder.handle.toString()),
            pos.x, pos.y, size.width, size.height, false
        ).sendAsync()
    }


    val webgpu = WebGPUSurfaceHolder(windowHolder)
    val worldRenderer = WorldRenderer(webgpu)

    init {
        context.world = worldRenderer
        context.gui = FunPanels()
        context.compose = ComposeHudWebGPURenderer(worldRenderer, show = false, onCreateScene = { scene ->
            scene.setContent {
                context.gui.PanelSupport()
            }
        })
    }
}

var crasharino = false

fun startTheFun(callback: () -> Unit) {
    FunContext {
        FunRenderer(WindowConfig())
        callback()
    }.start()
}

//fun main() {
//    val context = FunContext {
//        val base = FunRenderer(WindowConfig())
//        TestApp(base.worldRenderer)
//
//    }
//
//    context.start()
//}

class TestApp(val world: WorldRenderer) : Fun("TestApp") {
    val instance by cached(world.surfaceBinding) {
        val model = Model(Mesh.HomogenousCube, "Test")
        val value = object : Boundable {
            override val boundingBox: AxisAlignedBoundingBox
                get() = AxisAlignedBoundingBox.UnitAABB
        }
        world.spawn(
            id, world.getOrBindModel(model), value, Transform().toMatrix(), Tint(color = Color.Red)
        )
    }

    init {
        addGui({ Modifier.align(Alignment.BottomEnd) }) {
            Column {
                Surface(color = Color.White) {
                    Text("Halo!", color = Color.Black)
                }
                Button(onClick = {
                    println("Alo")
                }) {
                    Text(
                        "Foo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo FooFoo Foo",
                        fontSize = 20.sp
                    )
                }
            }

            LaunchedEffect(Unit) {
                delay(500)
                if (!crasharino) {
                    crasharino = true
                    throw NullPointerException("Alo")
                }
            }
        }
    }
}