package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.FunStateContext
import io.github.natanfudge.fn.core.FunStateManager
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.window.WindowConfig
import korlibs.time.milliseconds
import org.jetbrains.compose.reload.agent.Reload
import org.jetbrains.compose.reload.agent.invokeAfterHotReload
import org.jetbrains.compose.reload.core.mapLeft
import org.lwjgl.glfw.GLFW
import kotlin.time.Duration
import kotlin.time.TimeSource

class NewFunEvents : NewFun("FunEvents") {
    // see https://github.com/natanfudge/MineTheEarth/issues/115
    val beforeFrame by event<Duration>()
    val frame by event<Duration>()
    val afterFrame by event<Duration>()
    val beforePhysics by event<Duration>()
    val physics by event<Duration>()
    val afterPhysics by event<Duration>()
    val input by event<InputEvent>()
    val guiError by event<Throwable>()
    val appClosed by event<Unit>()
    val hotReload by event<Reload>()
}


private val maxFrameDelta = 300.milliseconds

class RootFun : NewFun(null, "") {

}

class NewFunContext(val appCallback: () -> Unit): FunStateContext {
    init {
        NewFunContextRegistry.setContext(this)
    }
    val initializer = FunInitializer()
    override val stateManager = FunStateManager()

    val rootFun = RootFun()

    val events = NewFunEvents()

    internal fun register(fn: NewFun) {
        stateManager.register(fn.id, allowReregister = true)
        initializer.requestInitialization(fn.id, fn.keys, fn)
    }


    private var previousFrameTime = TimeSource.Monotonic.markNow()

    private var pendingReload: Reload? = null

    fun start() {
        invokeAfterHotReload { _, result ->
            result.mapLeft {
                // This runs on a different thread which will cause issues, so we store it and will run it on the main thread later
                pendingReload = it
            }
        }

        events.hotReload.listenUnscoped {
            reload(it)
        }

        appCallback()
        // Nothing to close, but its fine, it will start everything without closing anything
        initializer.finishRefresh()

        while (true) {
            val elapsed = previousFrameTime.elapsedNow()
            previousFrameTime = TimeSource.Monotonic.markNow()
            val delta = elapsed.coerceAtMost(maxFrameDelta)

            events.beforePhysics(delta)
            events.physics(delta)
            events.afterPhysics(delta)

            events.beforeFrame(delta)
            events.frame(delta)
            events.afterFrame(delta)

            //TODO: it's likely this is too late, and we need to run this pending reload check more often, since e.g. a refresh
            // might happen after beforePhysics and then physics will have old state.
            // But tbh this is an uphill battle, it would be best to interrupt the main thread on reload and do what we need.
            if (pendingReload != null) {
                // Run reload on main thread
                events.hotReload.emit(pendingReload!!)
                pendingReload = null
            }
        }
    }

    private fun reload(reload: Reload) {
        events.appClosed(Unit)
        initializer.prepareForRefresh()
        appCallback()
        initializer.finishRefresh()
    }
}


internal object NewFunContextRegistry {
    private lateinit var context: NewFunContext
    fun setContext(context: NewFunContext) {
        this.context = context
    }

    fun getContext() = context
}

class StatelessEffect(
    id: String,
    override val keys: List<Any?>,
    val initFunc: () -> Unit,
    val close: () -> Unit,
) : NewFun(id) {
    override fun init() {
        initFunc()
    }

    override fun cleanup() {
        close()
    }
}

fun sideEffect(vararg keys: Any?, name: String = "sideEffect", close: () -> Unit = {}, init: () -> Unit)  {
    StatelessEffect(name, keys.toList(), init, close)
}

class FunBaseApp(window: WindowConfig) : NewFun("FunBaseApp") {
    init {
        sideEffect(Unit) {
            GlfwWindowProvider.initialize()
        }
    }


    //TODO: test hot reload and then write next steps
    val window = NewGlfwWindow(withOpenGL = false, showWindow = true, window)

    override fun init() {
        events.frame.listen {
            Thread.sleep(30)
            GLFW.glfwPollEvents()
            println("Frame")
        }
    }
}

fun main() {
    val context = NewFunContext {
        val base = FunBaseApp(WindowConfig())

    }



    context.start()


}