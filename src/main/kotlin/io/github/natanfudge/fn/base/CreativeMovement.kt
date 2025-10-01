package io.github.natanfudge.fn.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.core.exposeAsService
import io.github.natanfudge.fn.core.serviceKey
import io.github.natanfudge.fn.render.CameraMode

class CreativeMovement: Fun("Creative-Movement") {
    //TODO: this and all others ones don't need to be services anymore since we can just access them directly
    companion object {
        val service = serviceKey<CreativeMovement>()
    }

    init {
        exposeAsService(service)
    }

    private val _camera = renderer.camera

    var mode: CameraMode by funValue(CameraMode.Off)

    /**
     * Constant movement distance per frame. Not related to game time, because this is a dev tool.
     */
    var speed = 0.05f

    init {
        with(_camera) {
            input.mouseMoved.listen { delta ->
                val normalizedDeltaX = delta.x / renderer.windowSize.width
                val normalizedDeltaY = delta.y / renderer.windowSize.height

                when (mode) {
                    CameraMode.Orbital -> {
                        if (FunKey.Mouse(PointerButton.Tertiary) in input.heldKeys) {
                            if (delta.x != 0f || delta.y != 0f) {
                                pan(normalizedDeltaX * 20, normalizedDeltaY * 20)
                            }
                        }
                        if (FunKey.Mouse(PointerButton.Primary) in input.heldKeys) {
                            if (normalizedDeltaX != 0f) {
                                rotateX(normalizedDeltaX * 10)
                            }
                            if (normalizedDeltaY != 0f) {
                                rotateY(normalizedDeltaY * 10)
                            }
                        }
                    }

                    CameraMode.Fly -> {
                        tilt(normalizedDeltaX * 2, normalizedDeltaY * 2)
                    }

                    else -> {}
                }
            }

            input.registerHotkey("Creative Move Forward", Key.W, onHold = {
                ifNotOff {
                    moveForward(speed)
                }
            })

            input.registerHotkey("Creative Move Backward", Key.S, onHold = {
                ifNotOff {
                    moveBackward(speed)
                }
            })

            input.registerHotkey("Creative Move Left", Key.A, onHold = {
                ifNotOff {
                    moveLeft(speed)
                }
            })
            input.registerHotkey("Creative Move Right", Key.D, onHold = {
                ifNotOff {
                    moveRight(speed)
                }
            })

            input.registerHotkey("Creative Move Up", Key.Spacebar, onHold = {
                ifNotOff {
                    moveUp(speed)
                }
            })

            input.registerHotkey("Creative Move Down", Key.CtrlLeft, onHold = {
                ifNotOff {
                    moveDown(speed)
                }
            })
        }
    }

    private inline fun ifNotOff(block: () -> Unit) {
        if (mode != CameraMode.Off) {
            block()
        }
    }
    init {
        addGui {
            if (mode == CameraMode.Fly) {
                Box(Modifier.fillMaxSize().background(Color.Transparent)) {
                    Box(Modifier.size(2.dp, 20.dp).background(Color.Black).align(Alignment.Center))
                    Box(Modifier.size(20.dp, 2.dp).background(Color.Black).align(Alignment.Center))
                }
            }
        }
        events.worldInput.listen { input ->
            with(_camera) {
                when (input) {
                    is InputEvent.PointerEvent -> {
                        if (input.eventType == PointerEventType.Move && (mode == CameraMode.Orbital || mode == CameraMode.Off)) {
                            renderer.cursorPosition = (input.position)
                        }

                        if (input.eventType == PointerEventType.Scroll
                            && this.input.focused && mode == CameraMode.Orbital
                        ) {
                            val zoom = 1 + input.scrollDelta.y / 10
                            zoom(zoom)
                        }

                    }

                    is InputEvent.KeyEvent if input.event.type == KeyEventType.KeyUp -> {
                        setCameraMode(
                            when (input.event.key) {
                                Key.Escape -> CameraMode.Off
                                Key.O, Key.Grave -> CameraMode.Orbital
                                Key.F -> CameraMode.Fly
                                else -> mode // Keep existing
                            }
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    private fun setCameraMode(mode: CameraMode) {
        this.mode = mode

        setGUIFocused(mode == CameraMode.Off || mode == CameraMode.Orbital)
        setCursorLocked(mode == CameraMode.Fly)
    }
}



