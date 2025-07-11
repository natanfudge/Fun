package io.github.natanfudge.fn.test.physics

import io.github.natanfudge.fn.physics.Body
import io.github.natanfudge.fn.test.util.shouldRoughlyEqual
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class PhysicsAssertionBlock {
    val assertions = mutableListOf<Pair<Body, PhysicsAssertion>>()
    fun Body.shouldHave(position: Vec3f, velocity: Vec3f? = null, epsilon: Float = 1e-4f) {
        assertions.add(this to PhysicsAssertion(position, velocity, epsilon))
    }
}

data class PhysicsAssertion(
    val position: Vec3f,
    val velocity: Vec3f?,
    val epsilon: Float,
)

fun PhysicsAssertion.assert(body: Body, throwOnFailure: Boolean) {
    body.position.shouldRoughlyEqual(position, epsilon = epsilon, throwOnFailure = throwOnFailure)
    if(velocity != null) {
        body.velocity.shouldRoughlyEqual(velocity, epsilon = epsilon, throwOnFailure = throwOnFailure)
    }
}

