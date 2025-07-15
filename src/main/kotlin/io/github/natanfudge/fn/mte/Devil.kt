package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class Devil: Fun("devil") {
    val render = render(Model.fromGlbResource("files/models/hooded_devil.glb"))

    init {
        render.localTransform.translation = Vec3f(-4f, 0.5f, DeepSoulsGame.SurfaceZ + 0.5f)
        render.localTransform.rotation = Quatf.identity().rotateX(PIf / 2).rotateY(PIf /2)
    }
}