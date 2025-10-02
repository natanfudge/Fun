package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.physics.PhysicsSystem

class FunPhysics: Fun("FunPhysics") {
    val system = PhysicsSystem(logger)

    var tickAutomatically = true

    init {
        events.physicsTick.listen {
            if (tickAutomatically) {
                system.tick(it, spedUp = time.speed > 1.2f)
            }
        }
    }
}