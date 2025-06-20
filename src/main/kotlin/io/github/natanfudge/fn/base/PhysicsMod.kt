package io.github.natanfudge.fn.base

import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.physics.PhysicsSystem
import kotlin.time.Duration

class PhysicsMod: FunMod {
    val system = PhysicsSystem()
    override fun postPhysics(delta: Duration) {
        system.tick(delta)
    }
}