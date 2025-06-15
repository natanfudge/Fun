package io.github.natanfudge.fn.test.physics

import io.github.natanfudge.fn.physics.PhysicalFun
import io.github.natanfudge.fn.test.util.shouldRoughlyEqual
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class PhysicsAssertionBlock {
    val assertions = mutableListOf<Pair<PhysicalFun, PhysicsAssertion>>()
    fun PhysicalFun.shouldHave(position: Vec3f) {
        assertions.add(this to PhysicsAssertion(position))
    }
}

data class PhysicsAssertion(
    val position: Vec3f,
)

fun PhysicsAssertion.assert(body: PhysicalFun) {
    body.position.shouldRoughlyEqual(position)
}

