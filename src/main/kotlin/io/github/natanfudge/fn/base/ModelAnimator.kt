package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.physics.FunRenderState
import io.github.natanfudge.fn.render.Animation
import io.github.natanfudge.fn.render.ModelNode
import io.github.natanfudge.fn.render.SkeletalTransformation
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.fn.util.visit
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.time.Duration

interface FunResource {
    val context: FunContext
    fun alsoClose(closeable: AutoCloseable)
}

fun <T> EventStream<T>.listen(resource: FunResource, callback: (T) -> Unit) {
    val listener = listen(callback)
    resource.alsoClose(listener)
}


typealias JointMask = Set<Int>

class ActiveAnimation(
    val animation: Animation,
    var currentTime: Duration,
    /**
     * These 2 together specify which portion of the animation to play.
     */
    val minTime: Duration,
    val maxTime: Duration,
    val loop: Boolean,
    val jointMask: JointMask?,
) {
    override fun equals(other: Any?): Boolean {
        return other is ActiveAnimation && other.animation.name == animation.name
    }

    override fun hashCode(): Int {
        return animation.name.hashCode()
    }
}

//class AnimationGraph(
//
//)

class ModelAnimator(private val render: FunRenderState) {
    /**
     * Used to track which animations affect which bones, so we need which animations to run when multiply animations try to run at the same time.
     */
    private val animationsByJoint = mutableMapOf<Int, ActiveAnimation>()

    /**
     * Optimization to avoid doing too much work on play() when we are already running an animation,
     *  as well as in the render loop.
     * */
    private val activeAnimations = mutableSetOf<ActiveAnimation>()
//    private val animations = mutableListOf<ActiveAnimation>()

    //     var animation: ActiveAnimation? = null
//         private set
    private val model = render.model
    private val nodes = model.nodeHierarchy.toList()

    fun animationIsRunning(name: String) = activeAnimations.any { it.animation.name == name }

    init {
        render.context.events.frame.listen(render) { delta ->
            // Copy list to avoid ConcurrentModificationException
            for (animation in activeAnimations.toList()) {
                animation.currentTime = animation.currentTime + delta
                if (animation.currentTime > animation.maxTime) {
                    if (animation.loop) {
                        // We've reached the end - slide back to the start
                        animation.currentTime = animation.currentTime - animation.maxTime + animation.minTime
                    } else {
                        // We've reached the end - stop animating all related joints
                        for (affectedJoint in animation.animation.affectedJoints) {
                            animationsByJoint.remove(affectedJoint)
                        }
                        resolveActiveAnimations()
                        continue
                    }
                }
            }
            if (activeAnimations.isNotEmpty()) {
                val orderedLeftoverAnimations = activeAnimations.toList()
                val initialAnim = orderedLeftoverAnimations.last()

                // 1 - Use all bones from the first animation, independent of its mask, because we treat the mask as having an effect only
                // when other animations exist.
                val finalTransform: MutableMap<Int, Transform> = initialAnim.animation.sample(initialAnim.currentTime, nodes).toMutableMap()

                // 2. Layer additional animations, using only the masked joints.
                for (layeredAnimation in orderedLeftoverAnimations.dropLast(1)) {
                    val allTransforms = layeredAnimation.animation.sample(layeredAnimation.currentTime, nodes)
                    val masked = allTransforms.filter {
                        it.key in (layeredAnimation.jointMask ?: error("Animation layering should not happen we have an animation with no joint mask"))
                    }
                    finalTransform.putAll(masked)
                }
                render.renderInstance.setJointTransforms(finalTransform)
            }
        }
    }

    fun stop(animation: String) {
        val affectedJoints = animationsByJoint.filter { it.value.animation.name == animation }.keys
        for(affected in affectedJoints) {
            animationsByJoint.remove(affected)
        }
        resolveActiveAnimations()
    }

    /**
     * @param trimLastKeyframe Some animation loops have an identical last frame that causes some small lag at the end, so we can trim it.
     * @param specificallyAffectsJoints If set, existing animations with [specificallyAffectsJoints] set will not be removed, and
     * instead the transforms from [specificallyAffectsJoints] will be applied (both of this animation and the other animations).
     * Remaining joint transformations will be used according to order - the last animation's transforms will be used.
     * Important: if [specificallyAffectsJoints] is set, it is likely you will need to [stop] it manually.
     */
    fun play(animation: String, loop: Boolean = true, trimLastKeyframe: Boolean = loop, specificallyAffectsJoints: Set<String>? = null) {
        if (activeAnimations.any { it.animation.name == animation }) return
        // We assume (and enforce) that if there is an animation with no joint mask, it is the only animation.
        if (activeAnimations.isNotEmpty() && activeAnimations.first().jointMask == null) {
            // Clear the animation with no joint mask
            animationsByJoint.clear()
            activeAnimations.clear()
        }

        val animationObj = model.animations.find { it.name == animation }
            ?: error("Model ${model.id} does not contain animation '${animation}': existing animations: ${model.animations.map { it.name }}")

        val intJointMask = if (specificallyAffectsJoints != null) {
            val mask = mutableSetOf<Int>()
            model.nodeHierarchy.visit {
                if (it.name in specificallyAffectsJoints) mask.add(it.id)
            }
            mask
        } else null

        val affectedJoints = intJointMask ?: animationObj.affectedJoints

        val minTime = Duration.ZERO
        val currentTime = minTime
        val maxTime = if (trimLastKeyframe) animationObj.keyFrames[animationObj.keyFrames.size - 2].time else animationObj.keyFrames.last().time
        val activeAnimation = ActiveAnimation(animationObj, currentTime, minTime, maxTime, loop, intJointMask)
        for (joint in affectedJoints) {
            animationsByJoint[joint] = activeAnimation
        }
        resolveActiveAnimations()
    }

    /**
     * Aggregates the animations affecting all the joints into [activeAnimations]
     */
    private fun resolveActiveAnimations() {
        activeAnimations.clear()
        // Animations of the same name are considered equal so this won't duplicate animations
        activeAnimations.addAll(animationsByJoint.values)
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
    nodes: List<ModelNode>,
): SkeletalTransformation {
    if (keyFrames.isEmpty() || nodes.isEmpty()) return emptyMap()

    /* ------------------------------------------------------------------ *
     * 0.  Locate the two bracketing key-frames (prev ≤ time ≤ next)       *
     * ------------------------------------------------------------------ */
    val nextIdx = keyFrames.indexOfFirst { it.time >= time }
        .let { if (it == -1) keyFrames.lastIndex else it }
    val prevIdx = (nextIdx - 1).coerceAtLeast(0)

    val tPrev = keyFrames[prevIdx].time
    val tNext = keyFrames[nextIdx].time

    val alpha: Float =
        if (tPrev == tNext) 0f               // exact key or clip of one frame
        else ((time - tPrev).inWholeNanoseconds.toDouble() /
                (tNext - tPrev).inWholeNanoseconds.toDouble()).toFloat()
            .coerceIn(0f, 1f)


    /* ------------------------------------------------------------------ *
     * 1.  Build pose                                                     *
     * ------------------------------------------------------------------ */
    val pose = HashMap<Int, Transform>(nodes.size)

    nodes.forEach { joint ->
        val node = joint.id
        val base = joint.baseTransform                   // bind pose

        /* ---- pull nearest keyed values on each side (if any) ---- */
        var trBefore: Vec3f? = null
        var rotBefore: Quatf? = null
        var scBefore: Vec3f? = null
        run {
            for (i in prevIdx downTo 0) {
                keyFrames[i].transform[node]?.let { p ->
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
                keyFrames[i].transform[node]?.let { p ->
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
            trAfter != null -> trAfter
            else -> base.translation                        // bind pose
        }

        val rot = when {
            rotBefore != null && rotAfter != null -> rotBefore.slerp(rotAfter, alpha)
            rotBefore != null -> rotBefore
            rotAfter != null -> rotAfter
            else -> base.rotation
        }

        val sc = when {
            scBefore != null && scAfter != null -> scBefore.lerp(scAfter, alpha)
            scBefore != null -> scBefore
            scAfter != null -> scAfter
            else -> base.scale
        }

        pose[node] = Transform(tr, rot, sc)
    }

    return pose
}

