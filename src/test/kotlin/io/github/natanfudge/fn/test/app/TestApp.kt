package io.github.natanfudge.fn.test.app

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.files.readImage
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlinx.io.files.Path
import java.nio.file.Paths


class TestBody(
    id: String,
    model: Model,
    translate: Vec3f = Vec3f.zero(),
    rotate: Quatf = Quatf.identity(),
    scale: Vec3f = Vec3f(1f, 1f, 1f), color: Color = Color.White,
) : Fun(id) {
    val physics = physics()
    val render by render( model, physics)

    init {
        render(model, physics)
        physics.position = translate
        physics.orientation = rotate
        physics.scale = scale
        render.tint = Tint(color, 0f)
    }
}

class TestRenderObject(
    id: String,
    model: Model,
    translate: Vec3f = Vec3f.zero(),
    rotate: Quatf = Quatf.identity(),
    scale: Vec3f = Vec3f(1f, 1f, 1f),
    color: Color = Color.White,
) : Fun(id) {

    val render by render(model)

    init {
        render.localTransform.translation = translate
        render.localTransform.rotation = rotate
        render.localTransform.scale = scale
        render.tint = Tint(color, 0f)
    }
}




class FunPlayground : Fun("FunPlayground") {
    init {
        val kotlinImage = readImage(Paths.get("src/main/composeResources/drawable/Kotlin_Icon.png"))
        val wgpu4kImage = readImage(Paths.get("src/main/composeResources/drawable/wgpu4k-nodawn.png"))

        val cube = Model(Mesh.HomogenousCube, "Cube")
        val sphere = Model(Mesh.uvSphere(), "Sphere")
        TestRenderObject("X Axis", cube, scale = Vec3f(x = 10f, y = 0.1f, z = 0.1f), color = Color.Red) // X axis
        TestRenderObject("Y Axis",  cube, scale = Vec3f(x = 0.1f, y = 10f, z = 0.1f), color = Color.Green) // Y Axis
        TestRenderObject("W Axis",  cube, scale = Vec3f(x = 0.1f, y = 0.1f, z = 10f), color = Color.Blue) // Z Axis
        TestBody("Floor", cube, translate = Vec3f(0f, 0f, -1f), scale = Vec3f(x = 10f, y = 10f, z = 0.1f), color = Color.Gray).apply {
            physics.isImmovable = true
        }

        val kotlinSphere = sphere.copy(material = Material(texture = kotlinImage), id = "kotlin sphere")

        val wgpuCube = Model(Mesh.HeterogeneousCube, "wgpucube", Material(wgpu4kImage))

        val instance = TestBody("WGPU Cube", Model.fromGlbResource("files/models/miner.glb"), translate = Vec3f(-2f, 2f, 2f))
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


        TestBody("Kotlin Sphere", kotlinSphere)

        TestBody("Basic Sphere",  sphere, translate = Vec3f(2.8f, -2.8f, 2f))
        TestRenderObject("Light Sphere", sphere, translate = lightPos, scale = Vec3f(0.2f, 0.2f, 0.2f))

        TestBody("Basic Cube", cube, translate = Vec3f(4f, -4f, 0.5f), color = Color.Gray)
    }



}


fun main() {
    startTheFun {
         FunPlayground()
    }
}
