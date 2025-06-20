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
    val heldKeys = mutableSetOf<Key>()
    val heldMouseButtons = mutableSetOf<PointerButton>()
    var prevCursorPos: Offset? = null

    val focused: Boolean get() = prevCursorPos != null

    // TODO: this should go i think, hotkeys are better
    val keyHeld = EventStream.create<Key>()

    /**
     * Passes the movement delta
     */
    val mouseMoved = EventStream.create<Offset>()

    val hotkeys: List<Hotkey>
        field = mutableListOf<Hotkey>()


    //TODO: we need to improve this to include mouse keys...
    fun registerHotkey(
        name: String, defaultKey: Key,
        onHold: (delta: Float) -> Unit = {},
        onRelease: () -> Unit = {},
        onPress: () -> Unit = {}
    ): Hotkey {
        val hotkey = Hotkey(defaultKey, onPress, onRelease, onHold, name)
        hotkeys.add(hotkey)
        return hotkey
    }

    override fun prePhysics(delta: Duration) {
        if (focused) {
            for (hotkey in hotkeys) {
                if (hotkey.key in heldKeys) hotkey.onHold(delta.seconds.toFloat())
            }
            heldKeys.forEach { keyHeld.emit(it) } // SUS: should go
        }
    }


    override fun handleInput(input: InputEvent) {
        when (input) {
            is InputEvent.KeyEvent -> {
                val event = input.event
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        for (hotkey in hotkeys) {
                            if (hotkey.key == event.key) {
                                hotkey.onPress()
                            }
                        }
                        heldKeys.add(event.key)
                    }

                    KeyEventType.KeyUp -> {
                        for (hotkey in hotkeys) {
                            if (hotkey.key == event.key) {
                                hotkey.onRelease()
                            }
                        }
                        heldKeys.remove(event.key)
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
                            heldMouseButtons.add(input.button)
                        }
                    }

                    PointerEventType.Release -> {
                        if (input.button != null && focused) {
                            heldMouseButtons.remove(input.button)
                        }
                    }
                }
            }

            else -> {}
        }
    }
}

//enum class PressType {
//    Press, Release, Hold
//}

data class Hotkey(
//    val type: PressType,
    var key: Key,
    val onPress: () -> Unit,
    val onRelease: () -> Unit,
    val onHold: (delta: Float) -> Unit,
//    val action: () -> Unit,
    val name: String,
)
