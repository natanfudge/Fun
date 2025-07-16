package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.compose.ComposeHudWebGPURenderer
import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.render.DefaultCamera
import io.github.natanfudge.fn.render.FunSurface
import io.github.natanfudge.fn.render.FunWindow
import io.github.natanfudge.fn.util.MutEventStream
import io.github.natanfudge.fn.util.ValueHolder
import kotlin.time.Duration

class BaseFunEvents {
    val frame = MutEventStream<Duration>()
    val beforePhysics = MutEventStream<Duration>()
    val physics = MutEventStream<Duration>()
    val afterPhysics = MutEventStream<Duration>()
    val input = MutEventStream<InputEvent>()
    val guiError = MutEventStream<Throwable>()
    val appClose = MutEventStream<Unit>()

    internal fun clearListeners() {
        frame.clearListeners()
        beforePhysics.clearListeners()
        physics.clearListeners()
        afterPhysics.clearListeners()
        input.clearListeners()
        guiError.clearListeners()
        appClose.clearListeners()
    }
}

internal object FunContextRegistry {
    private lateinit var context: FunContext
    fun setContext(context: FunContext) {
        this.context = context
    }

    fun getContext() = context
}

class FunContext(
    private val surface: FunSurface, dims: ValueHolder<FunWindow>, private val compose: ComposeHudWebGPURenderer,
    private val stateContext: FunStateContext,
) : FunStateContext by stateContext, AutoCloseable {
    init {
        FunContextRegistry.setContext(this)
    }
    private val inputListener = surface.ctx.window.inputEvent.listenUnscoped { input ->
        // No need to block input with a null cursor position
        if (world.cursorPosition != null && input is InputEvent.PointerEvent &&
            // Allow blocking input by setting acceptMouseEvents to false
            !gui.acceptMouseEvents) return@listenUnscoped
        events.input.emit(input)
    }


    val events = BaseFunEvents()

    lateinit var time: FunTime

    val isClient = true

    val window by dims

    val world = surface.world
    val rootFuns = mutableMapOf<FunId, Fun>()

    var camera = DefaultCamera()

    private var restarting = false

    /**
     * Whether the app has hot reloaded at least once
     */
    var hotReloaded = false


    fun restartApp() {
        restarting = true
        rootFuns.forEach { it.value.close(unregisterFromParent = false, unregisterFromContext = false, deleteState = true) }

        ProcessLifecycle.restartByLabels(AppLifecycleName)
    }


    fun register(fn: Fun) {
        if (restarting) throw UnallowedFunException("Don't spawn Funs during cleanup of a Fun.")
        if (fn.isRoot) {
            rootFuns[fn.id] = fn
        }
        // We're gonna allow reregistering the fun state in case we hot reloaded, in order to reuse the state.
        // Before hot reload, there is no excuse to register the same Fun state twice.
        // After hot reload, the line gets blurry and there's no way to know whether a state is "before hot reload state" or "previous app state"
        // In the future we might seperate those, but this is good enough for now.
        stateContext.stateManager.register(fn.id, allowReregister = hotReloaded)
    }

    fun unregister(fn: Fun, deleteState: Boolean) {
        // We don't need to unregister anything because the entire context is getting thrown out, and this causes ConcurrentModificationException anyway
        if (restarting) return
        if (fn.isRoot) {
            rootFuns.remove(fn.id)
        }
        if (deleteState) {
            stateContext.stateManager.unregister(fn)
        }
    }

    val gui: Panels = Panels()


    fun setCursorLocked(locked: Boolean) {
        surface.ctx.window.cursorLocked = locked
        if (locked) world.cursorPosition = (null)
    }

    fun setGUIFocused(focused: Boolean) {
        compose.compose.windowLifecycle.assertValue.focused = focused
    }

    internal fun clean() {
        // Note that we don't clear the state context, we actually want to keep that around in order to preserver state.
        events.clearListeners()
        rootFuns.clear()
        gui.clearPanels()
    }

    override fun close() {
        events.appClose.emit(Unit)
        inputListener.close()
    }
}