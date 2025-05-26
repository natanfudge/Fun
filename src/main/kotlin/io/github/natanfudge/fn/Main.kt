package io.github.natanfudge.fn

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import io.github.natanfudge.fn.files.readImage
import io.github.natanfudge.fn.render.CameraMode
import io.github.natanfudge.fn.render.CubeUv
import io.github.natanfudge.fn.render.DefaultCamera
import io.github.natanfudge.fn.render.InputManager
import io.github.natanfudge.fn.render.Material
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.WorldRender
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

val lightPos = Vec3f(4f, -4f, 4f)

class FunPlayground(val context: FunContext) : FunApp {
    override val camera = DefaultCamera()
    val inputManager = InputManager()

    init {
        camera.bind(inputManager, context)
    }

    //TODO: passing WorldRender here is wrong  I think, it will make it hard to access it outside of renderInit which is not the intention
    // Should just add spawn() and bind() to FunContext
    override fun renderInit(world: WorldRender) {
        val kotlinImage = readImage(kotlinx.io.files.Path("src/main/composeResources/drawable/Kotlin_Icon.png"))
        val wgpu4kImage = readImage(kotlinx.io.files.Path("src/main/composeResources/drawable/wgpu4k-nodawn.png"))

        val cubeModel = Model(Mesh.UnitCube())
        val sphereModel = Model(Mesh.uvSphere())
        val cube = world.bind(cubeModel)
        cube.spawn(Mat4f.scaling(x = 10f, y = 0.1f, z = 0.1f), Color.Red) // X axis
        cube.spawn(Mat4f.scaling(x = 0.1f, y = 10f, z = 0.1f), Color.Green) // Y Axis
        cube.spawn(Mat4f.scaling(x = 0.1f, y = 0.1f, z = 10f), Color.Blue) // Z Axis
        cube.spawn(Mat4f.translation(0f, 0f, -1f).scale(x = 10f, y = 10f, z = 0.1f), Color.Gray)
        val sphere = world.bind(sphereModel)

        val kotlinSphere = world.bind(sphereModel.copy(material = Material(texture = kotlinImage)))

        val wgpuCube = world.bind(Model(Mesh.UnitCube(CubeUv.Grid3x2), Material(wgpu4kImage)))

        val instance = wgpuCube.spawn(Mat4f.translation(-2f, 2f, 2f))
        GlobalScope.launch {
            var i = 0
            while (true) {
                instance.setTransform(instance.transform.scaleInPlace(1.01f))

                if (i == 400) {
                    instance.despawn()
                    break
                }
                delay(10)
                i++
            }
        }


        kotlinSphere.spawn()
        sphere.spawn(Mat4f.translation(2f, 2f, 2f))
        sphere.spawn(Mat4f.translation(lightPos).scale(0.2f))

        cube.spawn(Mat4f.translation(4f, -4f, 0.5f), Color.Gray)
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




