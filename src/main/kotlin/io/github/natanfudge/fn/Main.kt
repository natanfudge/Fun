package io.github.natanfudge.fn

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.render.CameraMode
import io.github.natanfudge.fn.render.DefaultCamera
import io.github.natanfudge.fn.render.InputManager

//TODO:
// 13.6: Object selection overlay:
//    - Show/set state, including transform
//    - Visual transform system
// 14. Basic Physics
// 14.5b: State system
// 14.6 State-based physics (basic)
// 15. Trying making a basic game?
// 16. Better physics
// 17. PBR
// 18. Hotkeys
// 20. Backlog stuff i don't really care about atm:
// A. Deallocating GPU memory and free lists
// B. Find-grained picking with per-triangle ray intersection checks
// C. Various optimizations
// D. Blender-like selection outline  https://www.reddit.com/r/howdidtheycodeit/comments/1bdzr16/how_did_they_code_the_selection_outline_in_blender/?utm_source=chatgpt.com
// E. automatic GPU buffer expansion for expandable = true buffers
// F. MDI the render calls: waiting for MDI itself and bindless resources for binding the textures
// G. Expandable bound buffers - waiting for mutable bind groups
// H. Zoom based on ray casting on where the cursor is pointing at - make the focal point be the center of the ray-casted object.


fun DefaultCamera.bind(inputManager: InputManager, context: FunContext) {
    inputManager.mouseMoved.listen { delta ->
        val normalizedDeltaX = delta.x / context.windowWidth
        val normalizedDeltaY = delta.y / context.windowHeight

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


private fun DefaultCamera.handleInput(inputManager: InputManager, input: InputEvent, context: FunContext) {
    when (input) {
        is InputEvent.PointerEvent if input.eventType == PointerEventType.Scroll
                && inputManager.focused && mode == CameraMode.Orbital -> {
            val zoom = 1 + input.scrollDelta.y / 10
            zoom(zoom)
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

private fun DefaultCamera.setCameraMode(mode: CameraMode, context: FunContext) {
    this.mode = mode

    context.setGUIFocused(mode == CameraMode.Off)
    context.setCursorLocked(mode == CameraMode.Fly)
}


class FunPlayground(val context: FunContext) : FunApp {
    override val camera = DefaultCamera()
    val inputManager = InputManager()

    init {
        camera.bind(inputManager, context)
    }

    override fun physics(delta: Float) {
        inputManager.poll()
    }

    @Composable
    override fun gui() {
        ComposeMainApp()
    }

    override fun handleInput(input: InputEvent) {
        inputManager.handle(input)
        camera.handleInput(inputManager, input, context)
    }
}


fun main() {
    startTheFun {
        { FunPlayground(it) }
    }
}




