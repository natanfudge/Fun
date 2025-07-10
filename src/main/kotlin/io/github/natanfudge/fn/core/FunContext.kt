package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.FunStateContext
import io.github.natanfudge.fn.render.DefaultCamera
import io.github.natanfudge.fn.render.FunSurface
import io.github.natanfudge.fn.render.FunWindow
import io.github.natanfudge.fn.util.MutEventStream
import io.github.natanfudge.fn.util.ValueHolder
import kotlin.time.Duration

class BaseFunEvents {
    val frame = MutEventStream<Duration>()
    val beforePhysics = MutEventStream<Duration>()
    val afterPhysics = MutEventStream<Duration>()
    val input = MutEventStream<InputEvent>()
    val guiError = MutEventStream<Throwable>()
    val appClose = MutEventStream<Unit>()
}

internal object FunContextRegistry {
    private lateinit var context: FunContext
    fun setContext(context: FunContext) {
        this.context = context
    }

    fun getContext() = context
}

class FunContext(
    private val surface: FunSurface, dims: ValueHolder<FunWindow>, private val compose: ComposeWebGPURenderer,
    private val stateContext: FunStateContext,
) : FunStateContext by stateContext {


    val events = BaseFunEvents()

    lateinit var time: FunTime

    val isClient = true

    val window by dims

    val world = surface.world

    var camera = DefaultCamera()

    val rootFuns = mutableMapOf<FunId, Fun>()
    private var restarting = false


    fun restartApp() {
        restarting = true
        rootFuns.forEach { it.value.close(unregisterFromParent = false, unregisterFromContext = false) }

        ProcessLifecycle.restartByLabel(AppLifecycleName)
    }

    fun register(fn: Fun) {
        if (restarting) throw UnallowedFunException("Don't spawn Funs during cleanup of a Fun.")
        if (fn.isRoot) {
            rootFuns[fn.id] = fn
        }
        stateContext.stateManager.register(fn)
    }

    fun unregister(fn: Fun) {
        // We don't need to unregister anything because the entire context is getting thrown out, and this causes ConcurrentModificationException anyway
        if (restarting) return
        if (fn.isRoot) {
            rootFuns.remove(fn.id)
        }
        stateContext.stateManager.unregister(fn)
    }

    val gui: Panels = Panels()


    fun setCursorLocked(locked: Boolean) {
        surface.ctx.window.cursorLocked = locked
        if (locked) world.cursorPosition = (null)
    }

    fun setGUIFocused(focused: Boolean) {
        compose.compose.windowLifecycle.assertValue.focused = focused
    }
}