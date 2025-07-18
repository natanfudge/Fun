package io.github.natanfudge.fn.mte

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.mte.gui.addDsWorldPanel
import io.github.natanfudge.fn.physics.translation
import io.github.natanfudge.fn.render.Material
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

val FacingCameraRotation = Quatf.xRotation(PIf / -2)

class Devil(game: DeepSoulsGame): Fun("devil") {
    val render = render(Model.fromGlbResource("files/models/hooded_devil.glb"))


    init {
        render.localTransform.translation = Vec3f(-4f, 0.5f, DeepSoulsGame.SurfaceZ + 0.5f)
        render.localTransform.rotation = Quatf.identity().rotateX(PIf / 2).rotateY(PIf /2)

//        render.localTransform.scale = Vec3f(1f,1f,1f)

//        addDsWorldPanel(
        context.gui.addWorldPanel(
            Transform(
                translation = render.translation.shift(x = 2.2f, z = 1.6f),
                rotation = FacingCameraRotation,
                scale = Vec3f(4f,2f,1f)
            ),
            canvasHeight = 200,
            canvasWidth = 400
        ) {
//            Box(Modifier.fillMaxSize().background(Color.Transparent)) {
                Card(Modifier.fillMaxSize()) {
                    Text("Gather me 10 cats!")
                }
//            }
        }
    }


}

fun Vec3f.shift(x: Float = 0f, y: Float = 0f, z: Float = 0f) = copy(this.x +x, this.y + y, this.z +z)