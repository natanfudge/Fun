package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.gltf.PartialTransform
import io.github.natanfudge.fn.gltf.fromGlbResource
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.render
import io.github.natanfudge.fn.render.Animation
import io.github.natanfudge.fn.render.Joint
import io.github.natanfudge.fn.render.PartialSkeletonTransform
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.SkeletalTransformation
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.time.times
import kotlin.math.PI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

//TODO: test 2 different whale instances
class Whale(game: MineTheEarth) : Fun("whale", game.context) {


    val model = Model.fromGlbResource("files/models/whale.glb")


    // A map that maps a joint index to the original matrix transformation of a bone
    val currentTransform = mutableMapOf<Int, Mat4f>()
    val angle = 0.2f
//    fun animWhaleSkin() {
//        val skin = model.skeleton!!
//
//        // Now we can access skin.joints as expected
//        for (i in skin.joints.indices) {
//            // Index into the current joint
//            val (jointId, jointTransform) = skin.joints[i]
//
//            // If our map does
//            if (jointId !in currentTransform) {
//                currentTransform[jointId] = jointTransform
//            }
//
//            // Get the original position, rotation, and scale of the current joint
//            val origMatrix = currentTransform[jointId]!!;
//
//            val m = when (jointId) {
//                1, 0 -> origMatrix.rotateY(-angle)
//                3, 4 -> origMatrix.rotateX(if (jointId == 3) angle else -angle)
//                else -> origMatrix.rotateZ(angle)
//            }
//            // Apply the current transformation to the transform values within the relevant nodes
//            // (these nodes, of course, each being nodes that represent joints/bones)
//            currentTransform[jointId] = m
//        }
//    }


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

        val animation = model.animations[0]
        val joints = model.skeleton!!.joints
//        render.renderInstance.setJointTransforms(
//            animation.sample(125.milliseconds, joints)
////            keyFrames[1].second
//        )
//
//
        val duration = animation.keyFrames.last().first
//
        game.animation.animateLoop(duration) {
            render.renderInstance.setJointTransforms(
                animation.sample(duration * it, joints)
            )
        }.closeWithThis()
    }

}

/**
 * Samples this animation at [time] and returns a complete local-pose for the
 * requested [joints] – falling back to each joint’s bind pose when the clip
 * never touches a component.
 *
 * @return Map from *nodeIndex* → full Transform (translation, rotation, scale).
 */
fun Animation.sample(
    time: Duration,
    joints: List<Joint>,
): SkeletalTransformation {
    if (keyFrames.isEmpty() || joints.isEmpty()) return emptyMap()

    /* ------------------------------------------------------------------ *
     * 0.  Locate the two bracketing key-frames (prev ≤ time ≤ next)       *
     * ------------------------------------------------------------------ */
    val nextIdx = keyFrames.indexOfFirst { it.first >= time }
        .let { if (it == -1) keyFrames.lastIndex else it }
    val prevIdx = (nextIdx - 1).coerceAtLeast(0)

    val tPrev = keyFrames[prevIdx].first
    val tNext = keyFrames[nextIdx].first

    val alpha: Float =
        if (tPrev == tNext) 0f               // exact key or clip of one frame
        else ((time - tPrev).inWholeNanoseconds.toDouble() /
                (tNext - tPrev).inWholeNanoseconds.toDouble()).toFloat()
            .coerceIn(0f, 1f)


    /* ------------------------------------------------------------------ *
     * 1.  Build pose                                                     *
     * ------------------------------------------------------------------ */
    val pose = HashMap<Int, Mat4f>(joints.size)

    joints.forEach { joint ->
        val node = joint.nodeIndex
        val base = joint.baseTransform                   // bind pose

        /* ---- pull nearest keyed values on each side (if any) ---- */
        var trBefore: Vec3f? = null
        var rotBefore: Quatf? = null
        var scBefore: Vec3f? = null
        run {
            for (i in prevIdx downTo 0) {
                keyFrames[i].second[node]?.let { p ->
                    if (trBefore == null) trBefore = p.translation
                    if (rotBefore == null) rotBefore = p.rotation
                    if (scBefore == null) scBefore = p.scale
                    if (trBefore != null && rotBefore != null && scBefore != null) break
                }
            }
        }

        var trAfter: Vec3f? = null
        var rotAfter: Quatf? = null
        var scAfter: Vec3f? = null
        run {
            for (i in nextIdx..keyFrames.lastIndex) {
                keyFrames[i].second[node]?.let { p ->
                    if (trAfter == null) trAfter = p.translation
                    if (rotAfter == null) rotAfter = p.rotation
                    if (scAfter == null) scAfter = p.scale
                    if (trAfter != null && rotAfter != null && scAfter != null) break
                }
            }
        }

        /* ---- choose / interpolate each channel ---- */
        val tr = when {
            trBefore != null && trAfter != null -> trBefore.lerp(trAfter, alpha)
            trBefore != null -> trBefore
            trAfter  != null -> trAfter
            else              -> base.translation                        // bind pose
        }

        val rot = when {
            rotBefore != null && rotAfter != null -> rotBefore.slerp(rotAfter, alpha)
            rotBefore != null -> rotBefore
            rotAfter  != null -> rotAfter
            else              -> base.rotation
        }

        val sc = when {
            scBefore != null && scAfter != null -> scBefore.lerp(scAfter, alpha)
            scBefore != null -> scBefore
            scAfter  != null -> scAfter
            else             -> base.scale
        }

        pose[node] = Transform(tr, rot, sc).toMatrix()
    }

    return pose
}
