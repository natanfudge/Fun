package io.github.natanfudge.fn.base

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.core.exposeAsService
import io.github.natanfudge.fn.core.serviceKey
import korlibs.time.seconds



class InputManager: Fun("InputManager")  {
    companion object {
        val service = serviceKey<InputManager>()
    }

    init {
        exposeAsService(service)
    }

    val heldKeys = mutableSetOf<FunKey>()

    var prevCursorPos: Offset? = null

    val focused: Boolean get() = prevCursorPos != null


    //TODO: would like to replace this by EventEmitter.map{}
    /**
     * Passes the movement delta
     */
    val mouseMoved  by event<Offset>()


    private val _hotkeys: MutableList<Hotkey> = mutableListOf()

    val hotkeys: List<Hotkey> get() = _hotkeys


    /**
     * Scroll hotkeys are special, as they don't have the same notion of hold/press/release that other keys have.
     *
     * Instead, [onSignificantScrollDelta] is called when a sufficient scroll deltas was received for [defaultScrollKey],
     * and this corresponds to `onPress` of other keys, meaning if the hotkey was changed some non-scrolling key, then [onSignificantScrollDelta]
     * will be called when that key is pressed.
     *
     */
    fun registerHotkey(
        name: String,
        defaultScrollKey: ScrollDirection,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        onSignificantScrollDelta: () -> Unit,
    ) {
        registerHotkey(name, FunKey.ScrollKey(defaultScrollKey), ctrl, alt, shift, onPress = onSignificantScrollDelta)
    }

    /**
     * Note: Registers hotkeys that apply for the world - only when the GUI is not focused. For GUI hotkeys, it's recommended to use Compose APIs.
     */
    fun registerHotkey(
        name: String, defaultKey: Key,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        onHold: ((delta: Float) -> Unit)? = null,
        onRelease: (() -> Unit)? = null,
        onPress: (() -> Unit)? = null,
    ) = registerHotkey(name, FunKey.Keyboard(defaultKey), ctrl, alt, shift, onHold, onRelease, onPress)

    fun registerHotkey(
        name: String, defaultKey: PointerButton,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        onHold: ((delta: Float) -> Unit)? = null,
        onRelease: (() -> Unit)? = null,
        onPress: (() -> Unit)? = null,
    ) = registerHotkey(name, FunKey.Mouse(defaultKey), ctrl, alt, shift, onHold, onRelease, onPress)

    fun registerHotkey(
        name: String, defaultKey: FunKey,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        onHold: ((delta: Float) -> Unit)? = null,
        onRelease: (() -> Unit)? = null,
        onPress: (() -> Unit)? = null,
    ): Hotkey {
        val hotkey = Hotkey(pressed = defaultKey in heldKeys, defaultKey, onPress, onRelease, onHold, name, KeyboardModifiers(ctrl, alt, shift))
        _hotkeys.add(hotkey)
        return hotkey
    }

    //SLOW: make sure we stop creating FunKey instances constantly

    private fun Hotkey.modifiersPressed(): Boolean {
        if (modifiers.ctrl && FunKey.Keyboard(Key.CtrlLeft) !in heldKeys && FunKey.Keyboard(Key.CtrlRight) !in heldKeys) return false
        if (modifiers.alt && FunKey.Keyboard(Key.AltLeft) !in heldKeys && FunKey.Keyboard(Key.AltRight) !in heldKeys) return false
        if (modifiers.shift && FunKey.Keyboard(Key.ShiftLeft) !in heldKeys && FunKey.Keyboard(Key.ShiftRight) !in heldKeys) return false
        return true
    }

    init {
        events.beforePhysics.listen { delta ->
            if (focused) {
                for (hotkey in hotkeys) {
                    if (hotkey.key in heldKeys && hotkey.modifiersPressed()) hotkey.onHold?.invoke(delta.seconds.toFloat())
                }
            }
        }
        events.worldInput.listen { input ->
            when (input) {
                is InputEvent.KeyEvent -> {
                    val event = input.event
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            for (hotkey in hotkeys) {
                                if (hotkey.key.isKey(event.key) && !hotkey.isPressed && hotkey.modifiersPressed()) {
                                    hotkey.isPressed = true
                                    hotkey.onPress?.invoke()
                                }
                            }
                            heldKeys.add(FunKey.Keyboard(event.key))
                        }

                        KeyEventType.KeyUp -> {
                            for (hotkey in hotkeys) {
                                if (hotkey.key.isKey(event.key) && hotkey.modifiersPressed()) {
                                    hotkey.isPressed = false
                                    hotkey.onRelease?.invoke()
                                }
                            }
                            heldKeys.remove(FunKey.Keyboard(event.key))
                        }
                    }
                }

                is InputEvent.PointerEvent -> {
                    when (input.eventType) {
                        PointerEventType.Exit -> {
                            prevCursorPos = null
                        }

                        PointerEventType.Enter -> {
                            prevCursorPos = input.position
                        }

                        PointerEventType.Move -> {
                            val prev = prevCursorPos ?: run {
                                prevCursorPos = input.position
                                return@listen
                            }
                            prevCursorPos = input.position
                            val delta = prev - input.position
                            mouseMoved.emit(delta)
                        }

                        PointerEventType.Press -> {
                            if (input.button != null && focused) {
                                heldKeys.add(FunKey.Mouse(input.button))

                                for (hotkey in hotkeys) {
                                    if (hotkey.key.isMouseButton(input.button) && !hotkey.isPressed && hotkey.modifiersPressed()) {
                                        hotkey.isPressed = true
                                        hotkey.onPress?.invoke()
                                    }
                                }
                            }
                        }

                        PointerEventType.Release -> {
                            if (input.button != null && focused) {
                                heldKeys.remove(FunKey.Mouse(input.button))

                                for (hotkey in hotkeys) {
                                    if (hotkey.key.isMouseButton(input.button) && hotkey.modifiersPressed()) {
                                        hotkey.isPressed = false
                                        hotkey.onRelease?.invoke()
                                    }
                                }
                            }
                        }

                        PointerEventType.Scroll -> {
                            // SLOW we shouldn't go over all hotkeys each frame
                            for (hotkey in hotkeys) {
                                val key = hotkey.key
                                if (key is FunKey.ScrollKey) {
                                    val (deltaX, deltaY) = input.scrollDelta
                                    if (key.direction == ScrollDirection.Right && deltaX > 0) {
                                        hotkey.unconsumedScrollDistance += deltaX
                                    }
                                    if (key.direction == ScrollDirection.Left && deltaX < 0) {
                                        hotkey.unconsumedScrollDistance -= deltaX // Negate the delta
                                    }
                                    if (key.direction == ScrollDirection.Down && deltaY > 0.0) {
                                        hotkey.unconsumedScrollDistance += deltaY
                                    }
                                    if (key.direction == ScrollDirection.Up && deltaY < 0.0) {
                                        hotkey.unconsumedScrollDistance -= deltaY // Negate the delta
                                    }
                                    if (hotkey.unconsumedScrollDistance >= significantScrollDelta) {
                                        hotkey.unconsumedScrollDistance = 0f
                                        hotkey.onPress?.invoke()
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

private val significantScrollDelta = 1.0f

enum class ScrollDirection {
    Up, Down, Left, Right
}

//SUS: I would prefer not to wrap anything and have one FunKey class that has both mouse and keyboard keys
sealed interface FunKey {
    data class Keyboard(val value: Key) : FunKey
    data class Mouse(val value: PointerButton) : FunKey
    data class ScrollKey(val direction: ScrollDirection) : FunKey

    fun isKey(other: Key) = this is Keyboard && value == other
    fun isMouseButton(other: PointerButton) = this is Mouse && value == other
}

data class KeyboardModifiers(
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
)

class Hotkey(
    pressed: Boolean,
    var key: FunKey,
    // Note: if onHold/onRelease is not null, a hotkey key cannot be ScrollKey.
    val onPress: (() -> Unit)?,
    val onRelease: (() -> Unit)?,
    val onHold: ((delta: Float) -> Unit)?,
    val name: String,
    val modifiers: KeyboardModifiers,
) {
    // sus: can be replaced by the heldKeys list i think
    var isPressed: Boolean = pressed
        internal set

    /**
     * Used for scroll keys, we update and check this value to make the scroll callback only be called when a significant amount of scrolling was done.
     */
    internal var unconsumedScrollDistance: Float = 0f
}
