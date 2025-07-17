package io.github.natanfudge.fn.mte

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.sp
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.mte.gui.addDsWorldPanel
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.fn.window.WindowDimensions
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

data class PanelWindowDimensions(override val width: Int, override val height: Int) : WindowDimensions
class ChatBox(game: DeepSoulsGame) : Fun("ChatBox") {
    //    val render = render(Model(Mesh.UnitSquare, "ChatBox")).apply {
//        localTransform.translation = Vec3f(0f, 0f, 102f)
//        localTransform.rotation = Quatf.xRotation(PIf / -2)
//    }
//
//    companion object {
//        val window = ProcessLifecycle.bind<WindowDimensions>("Bar") {
//            PanelWindowDimensions(500, 500)
//        }
//        val tempBeforeFrame = MutEventStream<Duration>()
//        val renderer = ComposeOpenGLRenderer(
//            WindowParameters(), windowDimensionsLifecycle = window, beforeFrameEvent = tempBeforeFrame,
//            name = "Foo", show = true, onSetPointerIcon = {}, onError = {} //TODO
//        )
//
//        init {
//            window.start(Unit)
//            renderer.glfw.windowLifecycle.start(Unit)
//        }
//        //TODO: IOOB, lifecycle should provide a better diagnostic
//
//    }
    var counter by mutableStateOf(0)

    val panel = addDsWorldPanel(
        Transform(
            translation = Vec3f(0f, 0f, 102f),
            rotation = Quatf.xRotation(PIf / -2)
        ),
        200, 200
    ) {
        Surface {
            Column {
                Text("Hffalo $counter",  fontSize = 50.sp)
                Text("Halo?? xd",  fontSize = 50.sp)
            }
        }

    }

    init {
        game.input.registerHotkey("Test", Key.T) {
            counter++
        }
//        events.beforeFrame.listen {
//            tempBeforeFrame.emit(it)
//        }
//        renderer.windowLifecycle.assertValue.setContent {
//            Text("Halo $counter", color = Color.White, fontSize = 100.sp)
//        }
//        renderer.windowLifecycle.assertValue.frameStream.listen {
//            val image = FunImage(IntSize(it.width, it.height), it.bytes, null)
//            render.setTexture(image)
//        }

//        bgWindow.frameStream.listenUnscoped { (bytes, width, height) ->
//            dimensions.surface.device.copyExternalImageToTexture(
//                source = bytes,
//                texture = composeTexture,
//                width = width, height = height
//            )
//        }
//        render.setTexture(
//            FunImage.fromResource("files/background/sky_high.png"),
//            FunImage.fromResource("files/icons/items/goldore.png")
//        )
    }
}
