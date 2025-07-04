package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.mte.sample
import io.github.natanfudge.fn.physics.FunRenderState
import io.github.natanfudge.fn.render.Animation
import io.github.natanfudge.fn.util.EventStream
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



