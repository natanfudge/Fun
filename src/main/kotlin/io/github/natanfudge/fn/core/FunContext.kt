package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.compose.ComposeHudWebGPURenderer
import io.github.natanfudge.fn.core.newstuff.FunCache
import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.render.DefaultCamera
import io.github.natanfudge.fn.render.FunSurface
import io.github.natanfudge.fn.render.FunWindow
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.ValueHolder
import kotlin.time.Duration

class BaseFunEvents(
    //TODO: temporary until we consolidate rendering to use a FunContext itself
    val beforeFrame: EventEmitter<Duration>,
) {

    val beforePhysics = EventEmitter<Duration>()
    val physics = EventEmitter<Duration>()
    val afterPhysics = EventEmitter<Duration>()
    val input = EventEmitter<InputEvent>()
    val guiError = EventEmitter<Throwable>()
    val appClosed = EventEmitter<Unit>()

    internal fun checkListenersClosed() {
        //TODO: not checking  one because we are breaking some rules currently, the ComposeHudWebGPURenderer registers its beforeFrame once
        // and doesn't re-register it when the Context is re-created, in the future ComposeHudWebGPURenderer will be re-created and re-register on context
        // recreation
//        check(!beforeFrame.hasListeners)

        // In the future it will be expected that some listeners will remain, as some parts of the app won't reload.
        // We should still have this sort of test where everything is closed, and then we make sure that everything is empty, as a dev-time check.
        // (to verify the dev closed all his listeners properly)
        check(!beforePhysics.hasListeners) { "Before Physics" }
        check(!physics.hasListeners) { "Physics" }
        check(!afterPhysics.hasListeners) { "After Physics" }
        check(!input.hasListeners) { "Input" }
        check(!guiError.hasListeners) { "GUI error" }
        check(!appClosed.hasListeners) { "AppClose" }
//        beforeFrame.clearListeners()
//        beforePhysics.clearListeners()
//        physics.clearListeners()
//        afterPhysics.clearListeners()
//        input.clearListeners()
//        guiError.clearListeners()
//        appClose.clearListeners()
    }
}

internal object FunContextRegistry {
    private lateinit var context: FunContext
    fun setContext(context: FunContext) {
        this.context = context
    }

    fun getContext() = context
}

class FunContext internal constructor(
    private val surface: FunSurface, dims: ValueHolder<FunWindow>, private val compose: ComposeHudWebGPURenderer,
    private val stateContext: FunStateContext,
    //TODO: temporary until we consolidate rendering to use a FunContext itself
    val beforeFrame: EventEmitter<Duration>,
) : FunStateContext by stateContext, AutoCloseable {
    init {
        FunContextRegistry.setContext(this)
    }
    internal val cache = FunCache()

    private val inputListener = surface.ctx.window.inputEvent.listenUnscoped { input ->
        // No need to block input with a null cursor position
        if (world.cursorPosition != null && input is InputEvent.PointerEvent &&
            // Allow blocking input by setting acceptMouseEvents to false
            !gui.acceptMouseEvents
        ) return@listenUnscoped
        events.input.emit(input)
    }



    val events = BaseFunEvents(beforeFrame)

    lateinit var time: FunTime

    val isClient = true

    val window by dims

    val world = surface.world
    val rootFuns = mutableMapOf<FunId, Fun>()

    var camera = DefaultCamera()
    val logger = FunLogger()

    private var restarting = false

    /**
     * Whether the app has hot reloaded at least once
     */
    var hotReloaded = false


    fun restartApp() {
        restarting = true


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

    fun closeApp(deleteState: Boolean) {
        if (!deleteState) {
            hotReloaded = true
        }
        events.appClosed(Unit)

        rootFuns.forEach {
            it.value.close(
                // Doesn't matter
                unregisterFromParent = false,
                // We want to preserver state
                deleteState = deleteState,
                // The context is thrown out anyway, and this causes a CME on context.rootFuns
                unregisterFromContext = false
            )
        }
    }

    /**
     * Called before hot reload to restart the context without closing it
     */
    fun clean() {
        closeApp(deleteState = false)
        events.checkListenersClosed()
        rootFuns.clear()
        //TODO: GUIs will have a responsibility to clean up their GUIs once we start selectively initializing components
        gui.clearPanels()
    }


    override fun close() {
        closeApp(deleteState = true)
        inputListener.close() // This is scoped to the entire instance so it should die every clean()
    }
}