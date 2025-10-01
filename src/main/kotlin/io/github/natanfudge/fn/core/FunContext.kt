@file:OptIn(UnfunAPI::class)

package io.github.natanfudge.fn.core

import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.base.InputManager
import io.github.natanfudge.fn.compose.ComposeHudWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.render.WorldRenderer
import io.github.natanfudge.fn.render.isEmpty
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.filter
import io.github.natanfudge.fn.util.filterIsInstance
import io.github.natanfudge.fn.webgpu.WebGPUSurfaceHolder
import io.github.natanfudge.fn.window.GlfwWindowHolder
import io.github.natanfudge.fn.window.GlfwWindowProvider
import io.github.natanfudge.fn.window.MainThreadCoroutineDispatcher
import io.github.natanfudge.fn.window.WindowConfig
import korlibs.time.milliseconds
import org.jetbrains.compose.reload.agent.Reload
import org.jetbrains.compose.reload.agent.invokeAfterHotReload
import org.jetbrains.compose.reload.agent.invokeBeforeHotReload
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.core.mapLeft
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.TimeSource

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
    val anyInput by event<InputEvent>()

    // Events are not emitted here when the user focuses on the GUI
    val worldInput = anyInput.filter {
        check(!gui.closed)
        // Don't allow world pointer events when the user is in the GUI
        it !is InputEvent.PointerEvent || !gui.userInGui.value
    }
    val guiError by event<Throwable>()

    //    val appClosed by event<Unit>()
    val hotReload by event<Reload>()
    val closeButtonPressed = anyInput.filterIsInstance<InputEvent.CloseButtonPressed>()
    val pointer = anyInput.filterIsInstance<InputEvent.PointerEvent>()
    val key = anyInput.filterIsInstance<InputEvent.KeyEvent>()
    val windowMove = anyInput.filterIsInstance<InputEvent.WindowMove>()
    val windowResize = anyInput.filterIsInstance<InputEvent.WindowResize>()
    val afterWindowResize by event<IntSize>()
    val densityChange = anyInput.filterIsInstance<InputEvent.DensityChange>()
    val windowClose = anyInput.filterIsInstance<InputEvent.WindowClose>()
}


private val maxFrameDelta = 300.milliseconds

interface FunContext : FunStateContext {
    companion object {
        fun get() = FunContextRegistry.getContext()
    }
    val services: FunServices


    /**
     * You should generally use [Fun.coroutineScope] to spawn coroutines
     */
    @UnfunAPI
    val mainThreadCoroutineContext: CoroutineContext

    //SUS: consider making all of these services
    val events: FunEvents
    val time: FunTime
    val rootFun: RootFun
    val cache: FunCache
    val fsWatcher: FileSystemWatcher

    val gui: FunPanels get() = services.get(FunPanels.service)

    val renderer get() = services.get(WorldRenderer.service)

    val camera get() = renderer._camera

    val compose get() = services.get(ComposeHudWebGPURenderer.service)

    val logger: FunLogger get() = services.get(FunLogger.service)

    val input: InputManager get() = services.get(InputManager.service)

    fun restartApp()
    fun refreshApp(invalidTypes: List<KClass<*>>? = listOf())
    fun stopApp()

    //SUS: these 3 don't seem to belong here
    fun setCursorLocked(locked: Boolean)
    fun setGUIFocused(focused: Boolean)
    fun runPhysics(delta: Duration)

    fun register(fn: Fun)

    fun unregister(fn: Fun)
}

class FunContextImpl(val appCallback: FunContext.() -> Unit) : FunContext {
    init {
        FunContextRegistry.setContext(this)
    }

    override val mainThreadCoroutineContext = MainThreadCoroutineDispatcher()
//    override val globalCoroutineContext = CoroutineScope(dispatcher)

    override val cache = FunCache()
    override val stateManager = FunStateManager()

    override val rootFun = RootFun()

    override val events = FunEvents()


    override val fsWatcher = FileSystemWatcher()


    override val time = FunTime()

    override val services = FunServices()

    override fun register(fn: Fun) {
        stateManager.register(fn.id, allowReregister = true)
    }

    override fun unregister(fn: Fun) {
        stateManager.unregister(fn.id)
    }

    var running = true

    override fun stopApp() {
        running = false
    }


    private var previousFrameTime = TimeSource.Monotonic.markNow()

    private var pendingReload: Reload? = null
    private var jvmHotswapInProgress = false

    override fun setCursorLocked(locked: Boolean) {
        renderer.surfaceHolder.windowHolder._cursorLocked = locked
        if (locked) renderer.cursorPosition = (null)
    }

    override fun setGUIFocused(focused: Boolean) {
        compose.offscreenComposeRenderer.scene.focused = focused
    }

    internal fun start() {
        invokeBeforeHotReload {
            jvmHotswapInProgress = true
        }
        invokeAfterHotReload { _, result ->
            logDebug("HotReload"){"Hot Reloading app with classes: ${result.leftOrNull()?.definitions?.map { it.definitionClass.simpleName }}"}
            result.mapLeft {
                // This runs on a different thread which will cause issues, so we store it and will run it on the main thread later
                pendingReload = it
            }
            jvmHotswapInProgress = false
        }

        events.hotReload.listenUnscoped("Hot Reload") {
            reload(it)
        }

        events.closeButtonPressed.listenUnscoped("Exit App") {
            exitProcess(0)
        }

        // Window resizing stalls the main loop, so we want to interject frames when resizing so the content adapts to the resize as soon as the user
        // does it.
        events.afterWindowResize.listenUnscoped("Resize Frame") {
            frame()
        }


        appCallback()
        loop()
    }


    private fun checkClosed(vararg events: EventEmitter<*>) {
        for (event in events) {
            check(!event.hasListeners) { event.label }
        }
    }

    override fun runPhysics(delta: Duration) {
        checkHotReload()
        events.beforePhysics(delta)
        checkHotReload()
        events.physics(delta)
        checkHotReload()
        events.afterPhysics(delta)
    }

    private fun frame() {
        val elapsed = previousFrameTime.elapsedNow()
        previousFrameTime = TimeSource.Monotonic.markNow()
        val delta = elapsed.coerceAtMost(maxFrameDelta)

        fsWatcher.poll()
        time.advance(delta)

        checkHotReload()
        // Run coroutine calls on main thread
        mainThreadCoroutineContext.poll()
        checkHotReload()
        events.beforeFrame(delta)
        checkHotReload()
        events.frame(delta)
        checkHotReload()
        events.afterFrame(delta)
    }

    private fun checkHotReload() {
        while (jvmHotswapInProgress) {
            // Wait for hotswap to finish
            Thread.yield()
        }
        if (pendingReload != null) {
            // Run reload on main thread
            events.hotReload(pendingReload!!)
            pendingReload = null
        }
    }


    private fun loop() {
        while (running) {
            frame()
        }
    }

    /**
     * Closes and re-runs all [Fun]s with entirely new state
     */
    override fun restartApp() {
        refreshApp(invalidTypes = null)
    }

    /**
     * Closes and re-runs all [Fun]s.
     * if [invalidTypes] is null, all state will be deleted and all caches will be evicted
     */
    override fun refreshApp(invalidTypes: List<KClass<*>>?) {
        closeApp(invalidTypes)
        appCallback()
    }

    private fun closeApp(invalidTypes: List<KClass<*>>?) {
        cache.prepareForRefresh(invalidTypes)
        rootFun.close(unregisterFromParent = false, deleteState = invalidTypes == null)
        rootFun.clearChildren()
    }

    /**
     * Tests if all [Fun]s have correctly closed their callbacks when they have been closed
     */
    fun verifyFunsCloseListeners() = with(events) {
        closeApp(null)
        check(hotReload.listenerCount == 1) // The FunContext.start() listener
        check(anyInput.listenerCount == 1) {
            "Input listeners = ${anyInput.listenerCount} != 1: $anyInput"
        } // The FunContext.start() listener
        check(afterWindowResize.listenerCount == 1) {
            "Input listeners = ${afterWindowResize.listenerCount} != 1: $afterWindowResize"
        } // The FunContext.start() listener

        checkClosed(beforeFrame, frame, afterFrame, beforePhysics, physics, afterPhysics, guiError)
        appCallback()
    }

    private fun reload(reload: Reload) {
        aggregateDirtyClasses(reload)
        refreshApp(reload.definitions.map { it.definitionClass.kotlin })
    }
}


private fun aggregateDirtyClasses(reload: Reload) {
    val classes = mutableSetOf<String>()
    for (scope in reload.dirty.dirtyScopes) {
        classes.add(scope.methodId.classId.value)
    }
    logVerbose("HotReload"){ "Dirty classes: $classes" }
}


internal object FunContextRegistry {
    private lateinit var context: FunContext
    fun setContext(context: FunContext) {
        this.context = context
    }

    fun getContext() = context
}

fun getContext() = FunContextRegistry.getContext()


class FunBaseApp(config: WindowConfig) : Fun("FunBaseApp") {
    @Suppress("unused")
    val glfwInit by cached(InvalidationKey.None) {
        GlfwWindowProvider.initialize()
    }


    val windowHolder = GlfwWindowHolder(withOpenGL = false, showWindow = true, config, name = "WebGPU") {
        events.anyInput(it)
        if (it is InputEvent.WindowResize) {
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
    val _worldRenderer = WorldRenderer(webgpu)

    init {
        FunPanels()
        ComposeHudWebGPURenderer(_worldRenderer, show = false, onCreateScene = { scene ->
            scene.setContent {
                gui.PanelSupport()
            }
        })
    }
}

fun startTheFun(callback: FunContext.() -> Unit) {
    FunContextImpl {
        FunBaseApp(WindowConfig())
        FunLogger()
        InputManager()
        callback()
    }.start()
}
