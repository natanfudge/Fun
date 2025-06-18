package io.github.natanfudge.fn

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.ComposePanelPlacer
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.files.readImage
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.renderState
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

//TODO:
// 13. Basic Physics
// 14. Trying making a basic game?
// 15.   Visual transform system:
//          - Bounding box wireframe
//          - Translation Arrows
//          - Rotation Arrows
//          - Scale Arrows
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


val lightPos = Vec3f(4f, -4f, 4f)


class TestBody(
    id: String,
    app: FunPlayground, model: Model,
    translate: Vec3f = Vec3f.zero(),
    rotate: Quatf = Quatf.identity(),
    scale: Vec3f = Vec3f(1f, 1f, 1f), color: Color = Color.White,
) : Fun(id, app.context) {
    val render = renderState(model)
    val physics = physics(render, physics = app.physics.system)

    init {
        render.position = translate
        render.rotation = rotate
        render.scale = scale
        render.tint = Tint(color, 0f)
    }
}

class TestRenderObject(
    id: String,
    app: FunPlayground,
    model: Model,
    translate: Vec3f = Vec3f.zero(),
    rotate: Quatf = Quatf.identity(),
    scale: Vec3f = Vec3f(1f, 1f, 1f),
    color: Color = Color.White,
) : Fun(id, app.context) {

    val render = renderState(model)

    init {
        render.position = translate
        render.rotation = rotate
        render.scale = scale
        render.tint = Tint(color, 0f)
    }
}


// TODO: I think we can add the Mod system now, I have a pretty good understanding of how to use it.
class FunPlayground(override val context: FunContext) : FunApp() {
    val inputManager = installMod(InputManagerMod())

    var id = 0

    val physics = installMod(PhysicsMod())

    init {
        installMods(
            CreativeMovementMod(context, inputManager),
            VisualEditorMod(context),
        )

//        physics.system.gravity = false

        val kotlinImage = readImage(kotlinx.io.files.Path("src/main/composeResources/drawable/Kotlin_Icon.png"))
        val wgpu4kImage = readImage(kotlinx.io.files.Path("src/main/composeResources/drawable/wgpu4k-nodawn.png"))

        val cube = Model(Mesh.UnitCube(), "Cube")
        val sphere = Model(Mesh.uvSphere(), "Sphere")
        TestRenderObject("X Axis", this, cube, scale = Vec3f(x = 10f, y = 0.1f, z = 0.1f), color = Color.Red) // X axis
        TestRenderObject("Y Axis", this, cube, scale = Vec3f(x = 0.1f, y = 10f, z = 0.1f), color = Color.Green) // Y Axis
        TestRenderObject("W Axis", this, cube, scale = Vec3f(x = 0.1f, y = 0.1f, z = 10f), color = Color.Blue) // Z Axis
        TestBody("Floor", this, cube, translate = Vec3f(0f, 0f, -1f), scale = Vec3f(x = 10f, y = 10f, z = 0.1f), color = Color.Gray).apply {
            physics.isImmovable = true
        }
//        floor.color = Color.Blue


        val kotlinSphere = sphere.copy(material = Material(texture = kotlinImage), id = "kotlin sphere")

        val wgpuCube = Model(Mesh.UnitCube(CubeUv.Grid3x2), "wgpucube", Material(wgpu4kImage))

        val instance = TestBody("WGPU Cube", this, wgpuCube, translate = Vec3f(-2f, 2f, 2f))
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


        TestBody("Kotlin Sphere", this, kotlinSphere)

        TestBody("Basic Sphere", this, sphere, translate = Vec3f(2.8f, -2.8f, 2f))
        TestRenderObject("Light Sphere", this, sphere, translate = lightPos, scale = Vec3f(0.2f, 0.2f, 0.2f))

        TestBody("Basic Cube", this, cube, translate = Vec3f(4f, -4f, 0.5f), color = Color.Gray)
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun ComposePanelPlacer.gui() {
        FunPanel(Modifier.align(Alignment.CenterStart)) {
            Surface(color = Color.Transparent) {
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
    }


}


fun main() {
    startTheFun {
        { FunPlayground(it) }
    }
}
