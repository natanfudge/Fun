package io.github.natanfudge.fn.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.util.EventStream



class InputManager {
    val heldKeys = mutableSetOf<Key>()
    val heldMouseButtons = mutableSetOf<PointerButton>()
    var prevCursorPos: Offset? = null

    val focused: Boolean get() = prevCursorPos != null

    val keyHeld = EventStream.create<Key>()

    /**
     * Passes the movement delta
     */
    val mouseMoved = EventStream.create<Offset>()

    /**
     * Must be called before every frame
     */
    fun poll() {
        if (focused) {
            heldKeys.forEach { keyHeld.emit(it) }
        }
    }
    fun handle(input: InputEvent) {
        when (input) {
            is InputEvent.KeyEvent -> {
                val event = input.event
                when (event.type) {
                    KeyEventType.KeyDown -> heldKeys.add(event.key)
                    KeyEventType.KeyUp -> heldKeys.remove(event.key)
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
