package io.github.natanfudge.fn

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.natanfudge.fn.compose.utils.clickableWithNoIndication
import io.github.natanfudge.fn.compose.utils.mutableState
import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.files.readImage
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.FunState
import io.github.natanfudge.fn.physics.PhysicalFun
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

//TODO:
// 14. Basic Physics
// 15. Trying making a basic game?
// 13.   Visual transform system
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
        val normalizedDeltaX = delta.x / context.windowDimensions.width
        val normalizedDeltaY = delta.y / context.windowDimensions.height

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

private fun DefaultCamera.setCameraMode(mode: CameraMode, context: FunContext) {
    this.mode = mode

    context.setGUIFocused(mode == CameraMode.Off || mode == CameraMode.Orbital)
    context.setCursorLocked(mode == CameraMode.Fly)
}

val lightPos = Vec3f(4f, -4f, 4f)


class TestObject(
    app: FunPlayground, model: Model,
    translate: Vec3f = Vec3f.zero(),
    rotate: Quatf = Quatf.identity(),
    scale: Vec3f = Vec3f(1f, 1f, 1f), color: Color = Color.White,
) :
    PhysicalFun((app.id++).toString(), app.context, model, translate, rotate, scale, Tint(color, 0f)) {

}

// TODO: Physics. It should work in a GUI-less environment where we simulate ticks.
// I think change Physical to RenderedPhysical, and have Physical be just bounding box, translation and rotation. Physics doesn't care about scale.
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
        PhysicalFun(id = "foo", context, cube, scale = Vec3f(x = 10f, y = 0.1f, z = 0.1f), tint = Tint(Color.Red)) // X axis
        TestObject(this, cube, scale = Vec3f(x = 0.1f, y = 10f, z = 0.1f), color = Color.Green) // Y Axis
        TestObject(this, cube, scale = Vec3f(x = 0.1f, y = 0.1f, z = 10f), color = Color.Blue) // Z Axis
        val floor = TestObject(this, cube, translate = Vec3f(0f, 0f, -1f), scale = Vec3f(x = 10f, y = 10f, z = 0.1f), color = Color.Gray)
//        floor.color = Color.Blue

        val kotlinSphere = sphere.copy(material = Material(texture = kotlinImage), id = "kotlin sphere")

        val wgpuCube = Model(Mesh.UnitCube(CubeUv.Grid3x2), "wgpucube", Material(wgpu4kImage))

        val instance = TestObject(this, wgpuCube, translate = Vec3f(-2f, 2f, 2f))
//        GlobalScope.launch {
//            var i = 0
//            while (true) {
//                instance.scale = instance.scale * 1.01f
//
//                if (i == 400) {
//                    instance.close()
//                    break
//                }
//                delay(10)
//                i++
//            }
//        }


        TestObject(this, kotlinSphere)

        TestObject(this, sphere, translate = Vec3f(2f, 2f, 2f))
        TestObject(this, sphere, translate = lightPos, scale = Vec3f(0.2f, 0.2f, 0.2f))

        val obj = TestObject(this, cube, translate = Vec3f(4f, -4f, 0.5f), color = Color.Gray)
    }


    override fun physics(delta: Float) {
        inputManager.poll()
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun gui() {
        MaterialTheme(darkColorScheme()) {
            PanelSupport {
                Panel(Modifier.align(Alignment.CenterStart)) {
                    Surface {
                        Column {
                            Button(onClick = { ProcessLifecycle.restartByLabels(setOf("WebGPU Surface")) }) {
                                Text("Restart Surface Lifecycle")
                            }

                            Button(onClick = {
                                ProcessLifecycle.restartByLabels(setOf("App"))
                            }) {
                                Text("Restart App Lifecycle")
                            }
                            Button(onClick = {
                                ProcessLifecycle.restartByLabels(setOf("App Compose binding"))
                            }) {
                                Text("Reapply Compose App")
                            }

                            Button(onClick = {
                                FunHotReload.reloadStarted.emit(Unit)
                                FunHotReload.reloadEnded.emit(Unit)
                            }) {
                                Text("Emulate Hot Reload")
                            }
                        }
                    }
                }
                Panel(Modifier.align(Alignment.CenterEnd).padding(5.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))) {
                        Column(Modifier.padding(5.dp).width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
                            val selected = selectedObject
                            if (selected is Fun) {
                                val values = context.stateManager.getState(selected.id)
                                if (values != null) {
                                    Text(selected.id, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 30.sp)
                                    for ((key, value) in values.getCurrentState()) {
                                        value as FunState<Any?>
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(key, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Box(Modifier.weight(1f))
                                            value.editor.EditorUi(
                                                mutableState(value.value) { value.value = it }
                                            )
                                            Box(Modifier.weight(1f))
                                        }
                                    }
                                }

                            }

                        }
                    }
                }
            }
        }
    }


    /**
     * Allows placing "Panels", which block clicks from reaching the game when they are clicked.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun PanelSupport(panels: @Composable ComposePanelPlacer.() -> Unit) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().focusable().onPointerEvent(PointerEventType.Press) {
                // Allow clicking outside of the GUI
                acceptMouseEvents = true
                // The clickable thing is just for it to draw focus
            }.clickableWithNoIndication { }.onPointerEvent(PointerEventType.Enter) {
                acceptMouseEvents = true
            }
            )

            panels(ComposePanelPlacerWithBoxScope(this) { modifier, panel ->
                Box(
                    modifier
                        .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) {
                            // Block clicks
                            acceptMouseEvents = false
                        }.onPointerEvent(PointerEventType.Enter) {
                            acceptMouseEvents = false
                        }
                ) {
                    panel()
                }
            })

        }
    }


    var acceptMouseEvents = true

    private var mouseDownPos: Offset? = null

    private var hoveredObject: PhysicalFun? = null
    private var hoveredObjectOldTint: Tint? = null
    var selectedObject: PhysicalFun? by mutableStateOf(null)
    private var selectedObjectOldTint: Tint? = null


    override fun handleInput(input: InputEvent) {
        if (input is InputEvent.PointerEvent) {
            if (!acceptMouseEvents) {
                return
            }

            colorHoveredObject()

            if (input.eventType == PointerEventType.Press) {
                mouseDownPos = input.position
            }
            captureSelectedObject(input)
        }
        inputManager.handle(input)
        camera.handleInput(inputManager, input, context)

    }

    private fun captureSelectedObject(input: InputEvent.PointerEvent) {
        if (input.eventType == PointerEventType.Release) {
            val mouseDownPos = mouseDownPos ?: return
            // Don't reassign selected object if we dragged around too much
            if ((mouseDownPos - input.position).getDistanceSquared() < 100f) {
                val selected = context.world.hoveredObject as PhysicalFun?
                if (selectedObject != selected) {
                    // Restore the old color
                    selectedObject?.tint?.value = selectedObjectOldTint ?: Tint(Color.White)
                    selectedObject = selected
                    // Save color to restore later
                    selectedObjectOldTint = hoveredObjectOldTint
                    if(selected != null && hoveredObjectOldTint != null) {
                        selected.tint.value =  Tint(lerp(
                            hoveredObjectOldTint!!.color,
                            Color.White.copy(alpha = 0.5f),
                            0.8f
                        ) , strength = 0.8f)
                    }
                }

            }
        }
    }

    private fun colorHoveredObject() {
        if (hoveredObject != context.world.hoveredObject) {
            // Restore the old color
            if (hoveredObject != selectedObject) {
                hoveredObject?.tint?.value = hoveredObjectOldTint ?: Tint(Color.White)
            }

            val newHovered = (context.world.hoveredObject as? PhysicalFun)
            hoveredObject = newHovered
            // Save color to restore later
            hoveredObjectOldTint = newHovered?.tint?.value
            if (newHovered != selectedObject) {
                newHovered?.tint?.value = Tint(
                    lerp(hoveredObjectOldTint!!.color,Color.White.copy(alpha = 0.5f), 0.2f)
                    , strength = 0.2f)
            }
        }
    }

    /**
     * We want to detect which areas belong to the 2D GUI, so every panel placement goes through this interface.
     */
    interface ComposePanelPlacer : BoxScope {
        @Composable
        fun Panel(modifier: Modifier, panel: @Composable BoxScope.() -> Unit)
    }

    class ComposePanelPlacerWithBoxScope(
        scope: BoxScope, private val placer: @Composable (modifier: Modifier, panel: @Composable (BoxScope.() -> Unit)) -> Unit,
    ) : ComposePanelPlacer, BoxScope by scope {
        @Composable
        override fun Panel(
            modifier: Modifier,
            panel: @Composable (BoxScope.() -> Unit),
        ) {
            placer(modifier, panel)
        }

    }

}


fun main() {
    startTheFun {
        { FunPlayground(it) }
    }
}
