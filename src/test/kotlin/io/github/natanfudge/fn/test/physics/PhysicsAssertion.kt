package io.github.natanfudge.fn.test.physics

import io.github.natanfudge.fn.physics.SimplePhysicsObject
import io.github.natanfudge.fn.test.util.shouldRoughlyEqual
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class PhysicsAssertionBlock {
    val assertions = mutableListOf<Pair<SimplePhysicsObject, PhysicsAssertion>>()
    fun SimplePhysicsObject.shouldHave(position: Vec3f, epsilon: Float = 1e-4f) {
        assertions.add(this to PhysicsAssertion(position, epsilon))
    }
}

data class PhysicsAssertion(
    val position: Vec3f,
    val epsilon: Float,
)

fun PhysicsAssertion.assert(body: SimplePhysicsObject, throwOnFailure: Boolean) {
    body.render.position.shouldRoughlyEqual(position, epsilon = epsilon.toFloat(), throwOnFailure = throwOnFailure)
}

