package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.physics.PhysicsSystem
import kotlin.time.Duration


class FunPhysics(context: FunContext) {
    val system = PhysicsSystem()

    init {
        context.events.afterPhysics.listen {
            system.tick(it)
        }
    }
}