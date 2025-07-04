package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.physics.FunRenderState
import io.github.natanfudge.fn.render.Animation
import io.github.natanfudge.fn.render.ModelNode
import io.github.natanfudge.fn.render.SkeletalTransformation
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.time.Duration

interface FunResource {
    val context: FunContext
    fun alsoClose(closeable: AutoCloseable)
}

fun <T> EventStream<T>.listen(resource: FunResource, callback: (T) -> Unit) {
    val listener = listen(callback)
    resource.alsoClose(listener)
}

private class ActiveAnimation(
    val animation: Animation,
    var currentTime: Duration,
    /**
     * These 2 together specify which portion of the animation to play.
     */
    val minTime: Duration,
    val maxTime: Duration,
    val loop: Boolean,
)

class ModelAnimator(val render: FunRenderState) {
    private var animation: ActiveAnimation? = null
    private val model = render.model
    private val nodes = model.nodeHierarchy.toList()

    init {
        render.context.events.frame.listen(render) {
            val anim = animation
            if (anim != null) {
                anim.currentTime = anim.currentTime + it
                if (anim.currentTime > anim.maxTime) {
                    if (anim.loop) {
                        // We've reached the end - slide back to the start
                        anim.currentTime = anim.currentTime - anim.maxTime + anim.minTime
                    } else {
                        // We've reached the end - stop
                        animation = null
                        return@listen
                    }
                }
                render.renderInstance.setJointTransforms(
                    anim.animation.sample(anim.currentTime, nodes)
                )
            }
        }
    }

    /**
     * @param trimLastKeyframe Some animation loops have an identical last frame that causes some small lag at the end, so we can trim it.
     */
    fun play(animation: String, trimLastKeyframe: Boolean = true, loop: Boolean = true) {
        if (this.animation?.animation?.name == animation) return
        val animation = model.animations.find { it.name == animation }
            ?: error("Model ${model.id} does not contain animation '${animation}': existing animations: ${model.animations.map { it.name }}")

        val minTime = Duration.ZERO
        val currentTime = minTime
        val maxTime = if (trimLastKeyframe) animation.keyFrames[animation.keyFrames.size - 2].first else animation.keyFrames.last().first
        this.animation = ActiveAnimation(animation, currentTime, minTime, maxTime, loop)
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
    val pose = HashMap<Int, Mat4f>(nodes.size)

    nodes.forEach { joint ->
        val node = joint.id
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

