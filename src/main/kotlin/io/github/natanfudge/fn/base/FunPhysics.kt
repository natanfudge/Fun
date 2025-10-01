package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunLogger
import io.github.natanfudge.fn.core.exposeAsService
import io.github.natanfudge.fn.core.listen
import io.github.natanfudge.fn.core.serviceKey
import io.github.natanfudge.fn.physics.PhysicsSystem

class FunPhysics: Fun("FunPhysics") {
    val system = PhysicsSystem(logger)

    companion object {
        val service = serviceKey<FunPhysics>()
    }

    init {
        exposeAsService(service)
    }

    init {
        events.physics.listen {
            system.tick(it, spedUp = time.speed > 1.2f)
        }
    }
}