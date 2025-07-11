package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.FunOld
import io.github.natanfudge.fn.network.state.funValue
import kotlin.time.Duration


class RateLimiter(parent: FunOld, name: String = "RateLimit") : FunOld(parent, name) {
    private var lastInvocation: Duration by funValue(Duration.ZERO, "lastInvocation")


    /**
     * Will run [limited], but ONLY if [rate] has passed since the last time [limited] was successfully invoked.
     * If less time has passed, [limited] will not be invoked at all.
     *
     */
    fun run(rate: Duration, limited: () -> Unit) {
        if (context.time.gameTime - lastInvocation >= rate) {
            lastInvocation = context.time.gameTime
            limited()
        }
    }
}

