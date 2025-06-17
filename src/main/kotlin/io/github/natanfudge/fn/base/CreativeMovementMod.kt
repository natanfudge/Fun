package io.github.natanfudge.fn.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import io.github.natanfudge.fn.core.ComposePanelPlacer
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.render.CameraMode
import io.github.natanfudge.fn.render.InputManagerMod

class CreativeMovementMod(private val context: FunContext, private val inputManager: InputManagerMod) : FunMod {
    private val camera = context.camera

    init {
        with(camera) {
            inputManager.mouseMoved.listen { delta ->
                val normalizedDeltaX = delta.x / context.window.width
                val normalizedDeltaY = delta.y / context.window.height

                when (mode) {
                    CameraMode.Orbital -> {
                        if (PointerButton.Tertiary in inputManager.heldMouseButtons) {
                            if (delta.x != 0f || delta.y != 0f) {
                                pan(normalizedDeltaX * 20, normalizedDeltaY * 20)
                            }
                        }
                        if (PointerButton.Primary in inputManager.heldMouseButtons) {
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

            inputManager.keyHeld.listen { key ->
                val delta = 0.05f
                when (key) {
                    Key.W -> moveForward(delta)
                    Key.S -> moveBackward(delta)
                    Key.A -> moveLeft(delta)
                    Key.D -> moveRight(delta)
                    Key.Spacebar -> moveUp(delta)
                    Key.CtrlLeft -> moveDown(delta)
                }
            }
        }
    }

    @Composable
    override fun ComposePanelPlacer.gui() {
        if (camera.mode == CameraMode.Fly) {
            Box(Modifier.fillMaxSize().background(Color.Transparent)) {
                Box(Modifier.size(2.dp, 20.dp).background(Color.Black).align(Alignment.Center))
                Box(Modifier.size(20.dp, 2.dp).background(Color.Black).align(Alignment.Center))
            }
        }
    }

    override fun handleInput(input: InputEvent) = with(camera) {
        when (input) {
            is InputEvent.PointerEvent -> {
                if (input.eventType == PointerEventType.Scroll
                    && inputManager.focused && mode == CameraMode.Orbital
                ) {
                    val zoom = 1 + input.scrollDelta.y / 10
                    zoom(zoom)
                }
                if (input.eventType == PointerEventType.Move && (mode == CameraMode.Orbital || mode == CameraMode.Off)) {
                    context.world.setCursorPosition(input.position)
                }
            }

            is InputEvent.KeyEvent if input.event.type == KeyEventType.KeyUp -> {
                setCameraMode(
                    when (input.event.key) {
                        Key.Escape -> CameraMode.Off
                        Key.O, Key.Grave -> CameraMode.Orbital
                        Key.F -> CameraMode.Fly
                        else -> mode // Keep existing
                    }, context
                )
            }

            else -> {}
        }
    }

    private fun setCameraMode(mode: CameraMode, context: FunContext) {
        camera.mode = mode

        context.setGUIFocused(mode == CameraMode.Off || mode == CameraMode.Orbital)
        context.setCursorLocked(mode == CameraMode.Fly)
    }
}

