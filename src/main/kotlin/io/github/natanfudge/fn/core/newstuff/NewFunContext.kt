package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.window.GlfwWindow
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

class NewFunContext(val appCallback: () -> Unit) {
    init {
        NewFunContextRegistry.setContext(this)
    }
    val rootFun = RootFun()

    val cache = FunCache()
    val events = NewFunEvents()


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
        cache.finishRefresh()

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
        cache.prepareForRefresh()
        appCallback()
        cache.finishRefresh()
    }
}


internal object NewFunContextRegistry {
    private lateinit var context: NewFunContext
    fun setContext(context: NewFunContext) {
        this.context = context
    }

    fun getContext() = context
}

class FunBaseApp(window: WindowConfig) : NewFun("FunBaseApp") {
    val glfwConfig by memo {
        GlfwWindowProvider.initialize()
    }
    // TODO: maybe we should do something about side effects not occuring without memo, but tbh currently it's incorrect to not use memo
    // For userland, we def don't want memo everywhere, so we need to hook into the cache on init of Fun.

    val window by memo(window) {
        NewGlfwWindow(withOpenGL = false, showWindow = true, window)
    }


    init {
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