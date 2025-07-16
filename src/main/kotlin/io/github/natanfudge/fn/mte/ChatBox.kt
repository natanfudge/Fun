package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class ChatBox: Fun("ChatBox") {
    val render = render(Model(Mesh.UnitSquare, "ChatBox")).apply {
        localTransform.translation = Vec3f(0f,0f,102f)
        localTransform.rotation = Quatf.xRotation(PIf / 2)
    }

    init {
        render.setTexture(
            FunImage.fromResource("files/background/sky_high.png"),
//            FunImage.fromResource("files/icons/items/goldore.png")
        )
    }
}
