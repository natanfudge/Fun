package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.FunMod
import korlibs.time.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// SUS: I kinda wanna consider getting rid of some mods in favor of letting stuff hook into the lifecycles.
class AnimationMod : FunMod {
    override fun frame(deltaMs: Double) {
        for (animation in animations) {
            animation.loopTimePassed = min(animation.loopDuration, animation.loopTimePassed + deltaMs.milliseconds)
            if (animation.loopTimePassed >= animation.loopDuration) animation.loopTimePassed -= animation.loopDuration
            val fraction = animation.loopTimePassed / animation.loopDuration
            animation.callback(fraction.toFloat())
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
    internal val callback: (Float) -> Unit, val mod: AnimationMod,
    internal var loopDuration: Duration,
    internal var loopTimePassed: Duration = Duration.ZERO,
) : AutoCloseable {
    override fun close() {
        mod.stopAnimation(this)
    }
}
