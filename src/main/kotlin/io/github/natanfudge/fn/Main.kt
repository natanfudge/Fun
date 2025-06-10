package io.github.natanfudge.fn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.compose.LifecycleTree
import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.files.readImage
import io.github.natanfudge.fn.physics.PhysicalFun
import io.github.natanfudge.fn.render.*
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



class TestObject(app: FunPlayground, model: Model, transform: Mat4f = Mat4f.identity(), color: Color = Color.White) :
    PhysicalFun((app.id++).toString(), app.context, model, transform, color) {

}

//todo:
// 2.5 Simplified reset buttons
// 3. Object selection ui
class FunPlayground(val context: FunContext) : FunApp {
    override val camera = DefaultCamera()
    val inputManager = InputManager()

    var id = 0

    init {
        camera.bind(inputManager, context)

        val kotlinImage = readImage(kotlinx.io.files.Path("src/main/composeResources/drawable/Kotlin_Icon.png"))
        val wgpu4kImage = readImage(kotlinx.io.files.Path("src/main/composeResources/drawable/wgpu4k-nodawn.png"))

        val cube = Model(Mesh.UnitCube(), "Cube")
        val sphere = Model(Mesh.uvSphere(), "Sphere")
        PhysicalFun(id = "foo", context, cube, Mat4f.scaling(x = 10f, y = 0.1f, z = 0.1f), Color.Red) // X axis
        TestObject(this, cube, Mat4f.scaling(x = 0.1f, y = 10f, z = 0.1f), Color.Green) // Y Axis
        TestObject(this, cube, Mat4f.scaling(x = 0.1f, y = 0.1f, z = 10f), Color.Blue) // Z Axis
        TestObject(this, cube, Mat4f.translation(0f, 0f, -1f).scale(x = 10f, y = 10f, z = 0.1f), Color.Gray)

        val kotlinSphere = sphere.copy(material = Material(texture = kotlinImage), id = "kotlin sphere")

        val wgpuCube = Model(Mesh.UnitCube(CubeUv.Grid3x2), "wgpucube", Material(wgpu4kImage))

        val instance = TestObject(this, wgpuCube, Mat4f.translation(-2f, 2f, 2f))
        GlobalScope.launch {
            var i = 0
            while (true) {
                instance.transform = instance.transform.scaleInPlace(1.01f)

                if (i == 400) {
                    instance.despawn()
                    break
                }
                delay(10)
                i++
            }
        }


        TestObject(this, kotlinSphere)

        TestObject(this, sphere, Mat4f.translation(2f, 2f, 2f))
        TestObject(this, sphere, Mat4f.translation(lightPos).scale(0.2f))

        TestObject(this, cube, Mat4f.translation(4f, -4f, 0.5f), Color.Gray)
    }


    override fun physics(delta: Float) {
        inputManager.poll()
    }

    @Composable
    override fun gui() {
        MaterialTheme(darkColorScheme()) {
            Row(Modifier.fillMaxSize().background(Color.Transparent), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface {
                    Column {
                        Button(onClick = { ProcessLifecycle.restartByLabels(setOf("WebGPU Surface")) }) {
                            Text("Restart Render (+App)")
                        }
                        Button(onClick = { ProcessLifecycle.restartByLabels(setOf("App")) }) {
                            Text("Restart App")
                        }
                    }
                }
//                LifecycleTree(Modifier.size(500.dp))
                Surface {
                    Column {
                        Text("Stat 1")
                        Text("Stat 2")
                    }
                }
            }
        }
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




