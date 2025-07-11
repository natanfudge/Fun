package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.physics.PhysicsSystem


class FunPhysics(context: FunContext) {
    val system = PhysicsSystem()

    init {
        context.events.afterPhysics.listenPermanently {
            system.tick(it)
        }
    }
}