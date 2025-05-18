package io.github.natanfudge.fn.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.WindowCallbacks

enum class InputMode {
    Orbital,
    Fly,
    GUI,
}

class InputManager(val app: AppState, val window: WebGPUWindow, val compose: ComposeWebGPURenderer) {
    private val heldKeys = mutableSetOf<Key>()
    private val heldMouseKeys = mutableSetOf<PointerButton>()

    var focused = true

    var mode = InputMode.GUI
        set(value) {
            field = value
            compose.compose.windowLifecycle.assertValue.focused = value == InputMode.GUI
            getDim().window.cursorLocked = value == InputMode.Fly
        }

    val callbacks: WindowCallbacks = object : WindowCallbacks {
        override fun keyEvent(event: KeyEvent) {
            when (event.type) {
                KeyEventType.KeyDown -> heldKeys.add(event.key)
                KeyEventType.KeyUp -> {
                    heldKeys.remove(event.key)
                    onPress(event.key)
                }
            }
        }

        override fun pointerEvent(
            eventType: PointerEventType,
            position: Offset,
            scrollDelta: Offset,
            timeMillis: Long,
            type: PointerType,
            buttons: PointerButtons?,
            keyboardModifiers: PointerKeyboardModifiers?,
            nativeEvent: Any?,
            button: PointerButton?,
        ) {
            when (eventType) {
                PointerEventType.Exit -> {
                    focused = false
                }

                PointerEventType.Enter -> {
                    focused = true
                }

                PointerEventType.Move -> {
                    focused = true
                    val prev = prevMousePos ?: run {
                        prevMousePos = position
                        return
                    }
                    prevMousePos = position
                    val delta = prev - position
                    val dims = getDim()
                    val normalizedDeltaX = delta.x / dims.width
                    val normalizedDeltaY = delta.y / dims.height

                    when (mode) {
                        InputMode.Orbital -> {
                            if (PointerButton.Tertiary in heldMouseKeys) {
                                if (delta.x != 0f || delta.y != 0f) {
                                    app.camera.pan(normalizedDeltaX * 20, normalizedDeltaY * 20)
                                }
                            }
                            if (PointerButton.Primary in heldMouseKeys) {
                                if (normalizedDeltaX != 0f) {
                                    app.camera.rotateX(normalizedDeltaX * 10)
                                }
                                if (normalizedDeltaY != 0f) {
                                    app.camera.rotateY(normalizedDeltaY * 10)
                                }
                            }
                        }

                        InputMode.Fly -> {
                            app.camera.tilt(normalizedDeltaX * 2, normalizedDeltaY * 2)
                        }

                        else -> {}
                    }
                }

                PointerEventType.Press -> {
                    if (button != null && focused) {
                        heldMouseKeys.add(button)
                    }
                }

                PointerEventType.Release -> {
                    if (button != null && focused) {
                        heldMouseKeys.remove(button)
                    }
                }

                PointerEventType.Scroll -> {
                    if (focused && mode == InputMode.Orbital) {
                        val zoom = 1 + scrollDelta.y / 10
                        app.camera.zoom(zoom)
                    }
                }

            }
            super.pointerEvent(eventType, position, scrollDelta, timeMillis, type, buttons, keyboardModifiers, nativeEvent, button)
        }
    }
    var prevMousePos: Offset? = null

    fun poll() {
        heldKeys.forEach { whilePressed(it) }
    }

    private fun getDim() = window.dimensionsLifecycle.assertValue.dimensions


    fun onPress(key: Key) {
        if (!focused) return
        when (key) {
            Key.Escape -> mode = InputMode.GUI
            Key.O, Key.Grave -> mode = InputMode.Orbital
            Key.F -> mode = InputMode.Fly
        }
    }

    fun whilePressed(key: Key) {
        if (!focused) return
        val delta = 0.05f
        when (key) {
            Key.W -> app.camera.moveForward(delta)
            Key.S -> app.camera.moveBackward(delta)
            Key.A -> app.camera.moveLeft(delta)
            Key.D -> app.camera.moveRight(delta)
            Key.Spacebar -> app.camera.moveUp(delta)
            Key.CtrlLeft -> app.camera.moveDown(delta)

        }
    }
}