package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.files.Image
import io.ygdrasil.webgpu.GPUDevice

/**
 * Stores information about all instances of a [Model], for efficiency.
 */
class PreparedModel(val model: Model)

class WorldRender {
    fun draw(device: GPUDevice) {
        TODO()
    }

    fun spawn(model: PreparedModel) {
        TODO()
    }
}

fun Model.prepare()  = PreparedModel(this)

class Model(val mesh: Mesh, val material: Material)
class Material(val texture: Image)