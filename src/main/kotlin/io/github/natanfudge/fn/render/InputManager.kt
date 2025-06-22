package io.github.natanfudge.fn.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.util.EventStream
import korlibs.time.seconds
import kotlin.time.Duration


class InputManagerMod : FunMod {
    val heldKeys = mutableSetOf<FunKey>()

    var prevCursorPos: Offset? = null

    val focused: Boolean get() = prevCursorPos != null


    /**
     * Passes the movement delta
     */
    val mouseMoved = EventStream.create<Offset>()

    val hotkeys: List<Hotkey>
        field = mutableListOf<Hotkey>()


    fun registerHotkey(
        name: String, defaultKey: Key,
        onHold: (delta: Float) -> Unit = {},
        onRelease: () -> Unit = {},
        onPress: () -> Unit = {},
    ) = registerHotkey(name, FunKey.Keyboard(defaultKey), onHold, onRelease, onPress)

    fun registerHotkey(
        name: String, defaultKey: PointerButton,
        onHold: (delta: Float) -> Unit = {},
        onRelease: () -> Unit = {},
        onPress: () -> Unit = {},
    ) = registerHotkey(name, FunKey.Mouse(defaultKey), onHold, onRelease, onPress)

    fun registerHotkey(
        name: String, defaultKey: FunKey,
        onHold: (delta: Float) -> Unit = {},
        onRelease: () -> Unit = {},
        onPress: () -> Unit = {},
    ): Hotkey {
        val hotkey = Hotkey(pressed = defaultKey in heldKeys, defaultKey, onPress, onRelease, onHold, name)
        hotkeys.add(hotkey)
        return hotkey
    }

    override fun prePhysics(delta: Duration) {
        if (focused) {
            for (hotkey in hotkeys) {
                if (hotkey.key in heldKeys) hotkey.onHold(delta.seconds.toFloat())
            }
        }
    }


    override fun handleInput(input: InputEvent) {
        when (input) {
            is InputEvent.KeyEvent -> {
                val event = input.event
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        for (hotkey in hotkeys) {
                            if (hotkey.key.isKey(event.key)) {
                                if (!hotkey.isPressed) {
                                    hotkey.isPressed = true
                                    hotkey.onPress()
                                }
                            }
                        }
                        heldKeys.add(FunKey.Keyboard(event.key))
                    }

                    KeyEventType.KeyUp -> {
                        for (hotkey in hotkeys) {
                            if (hotkey.key.isKey(event.key)) {
                                hotkey.isPressed = false
                                hotkey.onRelease()
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
                            return
                        }
                        prevCursorPos = input.position
                        val delta = prev - input.position
                        mouseMoved.emit(delta)
                    }

                    PointerEventType.Press -> {
                        if (input.button != null && focused) {
                            heldKeys.add(FunKey.Mouse(input.button))
                        }
                    }

                    PointerEventType.Release -> {
                        if (input.button != null && focused) {
                            heldKeys.remove(FunKey.Mouse(input.button))
                        }
                    }
                }
            }

            else -> {}
        }
    }
}

//SUS: I would prefer not to wrap anything and have one FunKey class that has both mouse and keyboard keys
sealed interface FunKey {
    data class Keyboard(val value: Key) : FunKey
    data class Mouse(val value: PointerButton) : FunKey

    fun isKey(other: Key) = this is Keyboard && value == other
}


class Hotkey(
    pressed: Boolean,
    var key: FunKey,
    val onPress: () -> Unit,
    val onRelease: () -> Unit,
    val onHold: (delta: Float) -> Unit,
    val name: String,
) {
    var isPressed: Boolean = pressed
        internal set
}
