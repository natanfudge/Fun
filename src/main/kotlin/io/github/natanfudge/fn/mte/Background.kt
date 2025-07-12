package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.render.Material
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class Background : Fun("Background") {
    private val rows = 10
    private val columns = 10
    private val model = Model(Mesh.UnitSquare, "sky ", material = Material(FunImage.fromResource("files/background/sky_smooth.png")))
    val backgrounds = List(rows * columns) {
        val row = it / columns
        val column = it % columns
        render(model, name = "bg-$row-$column").apply {
            localTransform.rotation = Quatf.identity().rotateX(PIf / 2)
            localTransform.scale = Vec3f(20f,20f,20f)
            localTransform.translation = Vec3f((row - (rows / 2)) * 20f, 10f, (column - columns / 2) * 20f)
        }
    }
}