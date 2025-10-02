package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.Fun
import korlibs.time.min
import kotlin.time.Duration

class FunAnimation: Fun("FunAnimation")  {
    init {
        events.beforeFrame.listen { delta ->
            for (animation in animations) {
                animation.loopTimePassed = min(animation.loopDuration, animation.loopTimePassed + delta)
                if (animation.loopTimePassed >= animation.loopDuration) animation.loopTimePassed -= animation.loopDuration
                val fraction = animation.loopTimePassed / animation.loopDuration
                animation.callback(fraction.toFloat())
            }
        }
    }

    private val animations = mutableListOf<AnimationHandle>()


    fun animateLoop(loopDuration: Duration, callback: (fraction: Float) -> Unit): AnimationHandle {
        val handle = AnimationHandle(callback, this, loopDuration)
        animations.add(handle)

        return handle
    }

    fun stopAnimation(handle: AnimationHandle) {
        animations.remove(handle)
    }
}

class AnimationHandle(
    internal val callback: (Float) -> Unit, val mod: FunAnimation,
    internal var loopDuration: Duration,
    internal var loopTimePassed: Duration = Duration.ZERO,
) : AutoCloseable {
    override fun close() {
        mod.stopAnimation(this)
    }
}
