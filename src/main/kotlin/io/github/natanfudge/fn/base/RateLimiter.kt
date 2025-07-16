//package io.github.natanfudge.fn.base
//
//import io.github.natanfudge.fn.core.Fun
//import io.github.natanfudge.fn.network.state.funValue
//import kotlin.time.Duration
//
//
//class RateLimiter(parent: Fun, name: String = "RateLimit") : Fun(parent, name) {
//    private var lastInvocation: Duration by funValue(Duration.ZERO, "lastInvocation")
//
//
//    /**
//     * Will run [limited], but ONLY if [rate] has passed since the last time [limited] was successfully invoked.
//     * If less time has passed, [limited] will not be invoked at all.
//     *
//     */
//    fun run(rate: Duration, limited: () -> Unit) {
//        if (context.time.gameTime - lastInvocation >= rate) {
//            lastInvocation = context.time.gameTime
//            limited()
//        }
//    }
//}
//
