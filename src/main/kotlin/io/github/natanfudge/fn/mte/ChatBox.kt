package io.github.natanfudge.fn.mte

import androidx.compose.material.Text
import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.ComposeOpenGLRenderer
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
import io.github.natanfudge.fn.window.WindowDimensions
import io.github.natanfudge.fn.window.WindowParameters
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

data class PanelWindowDimensions(override val width: Int, override val height: Int) : WindowDimensions
class ChatBox : Fun("ChatBox") {
    val render = render(Model(Mesh.UnitSquare, "ChatBox")).apply {
        localTransform.translation = Vec3f(0f, 0f, 102f)
        localTransform.rotation = Quatf.xRotation(PIf / 2)
    }

    init {
        val window = ProcessLifecycle.bind<WindowDimensions>("Bar") {
            PanelWindowDimensions(500, 500)
        }
        val renderer = ComposeOpenGLRenderer(
            WindowParameters(), windowDimensionsLifecycle = window, beforeFrameEvent = context.beforeFrame,
            name = "Foo", show = true, onSetPointerIcon = {}, onError = {} //TODO
        )
        window.start(Unit)
        //TODO: IOOB, lifecycle should provide a better diagnostic
        renderer.glfw.windowLifecycle.start(Unit)
        renderer.windowLifecycle.assertValue.setContent {
            Text("Halo", color = Color.White)
        }

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
