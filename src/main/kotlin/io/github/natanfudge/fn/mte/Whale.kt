package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.render
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.seconds
import kotlin.math.PI
import kotlin.math.roundToInt

//TODO: test 2 different whale instances
class Whale(game: MineTheEarth) : Fun("whale", game.context) {


    val model = Model.fromGlbResource("files/models/whale.glb")


    // A map that maps a joint index to the original matrix transformation of a bone
    val currentTransform = mutableMapOf<Int, Mat4f>()
    val angle = 0.2f
    fun animWhaleSkin() {
        val skin = model.skeleton!!

        // Now we can access skin.joints as expected
        for (i in skin.joints.indices) {
            // Index into the current joint
            val (jointId, jointTransform) = skin.joints[i]

            // If our map does
            if (jointId !in currentTransform) {
                currentTransform[jointId] = jointTransform
            }

            // Get the original position, rotation, and scale of the current joint
            val origMatrix = currentTransform[jointId]!!;

            val m = when (jointId) {
                1, 0 -> origMatrix.rotateY(-angle)
                3, 4 -> origMatrix.rotateX(if (jointId == 3) angle else -angle)
                else -> origMatrix.rotateZ(angle)
            }
            // Apply the current transformation to the transform values within the relevant nodes
            // (these nodes, of course, each being nodes that represent joints/bones)
            currentTransform[jointId] = m
        }
    }


    val render = render(model)

    init {
        render.localTransform.translation = Vec3f(-2f, 0.5f, 12f)
        render.localTransform.rotation = render.localTransform.rotation.rotateX(PI.toFloat() / -2)

        //TODO:
        // 1. Interpolation - that should fix the animation issue, the interoplated value is the correct one, not the bind pose.
        // 2. Proper animation API, where you select with a string
        // 3. Fix the joe animation (not related to code)

        // present node indices : 0 1 2 5
        // Tree:
        // 5        - Head
        //  2       - Spine
        //    1     - Lower body
        //      0   - Tail
        //  3       - Left Fin
        //  4       - Right Fin

        val keyFrames = model.animations[1].keyFrames
        render.renderInstance.setJointTransforms(
            keyFrames[1].second
        )




//
//        game.animation.animateLoop(keyFrames.last().first * 2) {
//            val index = (it * (keyFrames.size - 1)).roundToInt()
//            println("Index: $index")
//            render.renderInstance.setJointTransforms(
//                keyFrames[1].second
//            )
//        }.closeWithThis()
    }

}
