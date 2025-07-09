package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.base.sample
import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.render
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.times

class Whale(game: MineTheEarth) : Fun("whale", game.context) {

    val model = Model.fromGlbResource("files/models/whale.glb")
    val physics = physics(game.physics.system)

    val render = render(model, physics)

    init {
        physics.position = Vec3f(-2f, 0.5f, 12f)
        val animation = model.animations[1]
        val nodes = model.nodeHierarchy.toList()
        val duration = animation.keyFrames.last().time
        game.animation.animateLoop(duration) {
            render.renderInstance.setJointTransforms(
                animation.sample(duration * it, nodes)
            )
        }.closeWithThis()
    }

}
