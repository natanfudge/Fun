package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.physics.PhysicsSystem


class FunPhysics(val context: FunContext) {
    val system = PhysicsSystem()

    init {
        context.events.physics.listenUnscoped {
            system.tick(it, spedUp = context.time.speed > 1.2f)
        }
    }
}