package io.github.natanfudge.fn.core

import kotlin.time.Duration
import kotlin.time.TimeSource

class FunTime : Fun("time") {
    var speed by funValue(1f)
    fun skip(time: Duration) {
        runPhysics(time)
    }

    var stopped by funValue(false)

    fun stop() {
        stopped = true
    }

    fun resume() {
        stopped = false
        prevPhysicsTime = TimeSource.Monotonic.markNow()
    }

    var gameTime: Duration by funValue(Duration.ZERO)

    private var prevPhysicsTime = TimeSource.Monotonic.markNow()

    internal fun advance(realTimeDelta: Duration) {
        if (stopped) return

        val actualDelta = realTimeDelta * speed.toDouble()
        gameTime += actualDelta

        runPhysics(actualDelta)
    }

}