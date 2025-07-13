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
import kotlin.math.roundToInt

class Background : Fun("Background") {
    private var builtHeight = 0f
    private val worldWidth = 200f
    private val bgRotation = Quatf.identity().rotateX(PIf / 2)

    private fun backgroundLayer(tileSize: Float, tilesInHeight: Int, image: String) {
        val model = Model(Mesh.UnitSquare, image, material = Material(FunImage.fromResource("files/background/$image.png")))
        val columns = (worldWidth / tileSize).roundToInt()
        // Start from negative column values to be centered at (0,0)
        val baseColumn = -(columns / 2)
        val scale = Vec3f(tileSize, tileSize, tileSize)
        repeat(tilesInHeight) { row ->
            repeat(columns) { col ->
                render(model, name = "bg-$image-$row-$col").apply {
                    localTransform.scale = scale
                    localTransform.translation = Vec3f((baseColumn + col) * tileSize, 10f, builtHeight + tileSize / 2)
                    localTransform.rotation = bgRotation
                }
            }
            builtHeight += (tileSize)
        }
    }
    init {
        backgroundLayer(20f, 4, "underground")
        backgroundLayer(20f, 1, "surface")
        backgroundLayer(20f, 1, "sky_low_to_surface")
        backgroundLayer(20f, 1, "sky_low")
        backgroundLayer(40f, 1, "sky_high_to_low")
        backgroundLayer(40f, 15, "sky_high")
    }
}