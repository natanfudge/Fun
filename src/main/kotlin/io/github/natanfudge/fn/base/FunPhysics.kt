package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.listen
import io.github.natanfudge.fn.physics.PhysicsSystem

//TODO: only wants AutoClose
class FunPhysics: Fun("FunPhysics") {
    val system = PhysicsSystem(context.logger)

    init {
        context.events.physics.listen {
            system.tick(it, spedUp = context.time.speed > 1.2f)
        }
    }
}