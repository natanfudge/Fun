package io.github.natanfudge.fn.base

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.Tint
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class SimpleArrow(val color: Color, id: String) : Fun(id) {
    //TODO: use a 0-size cube, then try to make it a bit better
//    val root by render(Model(Mesh.))
    val cylinder by render(Model(Mesh.cylinder(4.0f),"ArrowCylinder"))
    val tip by render(Model(Mesh.arrowHead( PIf / 2.5f), "ArrowHead"), cylinder)
    init {
        cylinder.tint = Tint(color,0.5f)
        tip.tint = Tint(color,0.5f)
        tip.localTransform.translation = Vec3f(0f,0f,4f)
        tip.localTransform.rotation = Quatf.xRotation(PIf/2)
        tip.localTransform.scale = Vec3f(2f,4f,2f)
    }
}


// TODO BUG: stuff sometimes stays white-out selected after we are no longer selecting it with visual editor