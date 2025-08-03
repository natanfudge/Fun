package io.github.natanfudge.fn.mte

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.network.state.listenAsState
import io.github.natanfudge.fn.physics.translation
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

val FacingCameraRotation = Quatf.xRotation(PIf / -2)

class Devil() : Fun("devil") {
    val render by render(Model.fromGlbResource("files/models/hooded_devil.glb"))
//    val foo by render(Model.quad("files/background/sky_high.png"), render)
    var quotaRequested by funValue(false)


    init {
        render.localTransform.translation = Vec3f(-4f, 0.5f, DeepSoulsGame.SurfaceZ + 0.5f)
        render.localTransform.rotation = Quatf.identity().rotateX(PIf / 2).rotateY(PIf / 2)

//        render.localTransform.scale = Vec3f(1f,1f,1f)

//        addDsWorldPanel(
        context.gui.addWorldPanel(
            Transform(
                translation = render.translation.shift(x = 2.2f, z = 1.6f),
                rotation = FacingCameraRotation,
                scale = Vec3f(4f, 2f, 1f)
            ),
            canvasHeight = 200,
            canvasWidth = 400
        ) {
            if (::quotaRequested.listenAsState().value) {
                Card(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                            ResourceImage("drawable/blocks/gold.png", Modifier.padding(20.dp).size(140.dp))
                            Text("X 10", fontSize = 100.sp, color = Color(150, 150, 0))
                        }
                    }
                }
            }
        }

    }


}

fun Vec3f.shift(x: Float = 0f, y: Float = 0f, z: Float = 0f) = copy(this.x + x, this.y + y, this.z + z)