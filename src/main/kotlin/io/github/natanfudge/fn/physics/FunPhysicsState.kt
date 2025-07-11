package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunOld
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.setValue

//TODO: remove placeholder Funid, FunOld shouldn't want this
class FunPhysicsState(override val id: String) : Fun(), Body, Transformable {
    val physicsTransform = FunTransform(child("transform"))
    override var transform by physicsTransform::transform
    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        return physicsTransform.onTransformChange(callback)
    }

    override val boundingBox: AxisAlignedBoundingBox
        //TODO: optimize
        get() = baseAABB.transformed(transform.toMatrix())

//    onTransformChange {
//        boundingBox = baseAABB.transformed(transform.toMatrix())
//    }
//    state::baseAABB.getBackingState().beforeChange {
//        this.boundingBox = it.transformed(transform.toMatrix())
//    }

    var baseAABB by funValue(AxisAlignedBoundingBox.UnitAABB, "baseAABB")

    override var velocity: Vec3f by funValue(Vec3f.zero(), "velocity")
    override var acceleration: Vec3f by funValue(Vec3f.zero(), "acceleration")
    override var angularVelocity: Vec3f by funValue(Vec3f.zero(), "angularVelocity")
    override var isGrounded: Boolean by funValue(true, "isGrounded")
    override var affectedByGravity: Boolean by funValue(true, "affectedByGravity")
    override var mass: Float by funValue(1f, "mass")
    override var isImmovable: Boolean by funValue(false, "isImmovable")
    override var collisionGroup: Int by funValue(0, "collisionGroup")

    override var position by physicsTransform::translation

    override var orientation by physicsTransform::rotation
}

//


class FunBody(
    funParent: FunOld,
    private val physics: PhysicsSystem,
    val state: FunPhysicsState,
) : FunOld(funParent, "physics-placeholder"), Body by state, Transformable by state {
    init {
        physics.add(this)
    }

    var baseAABB by state::baseAABB
    var scale: Vec3f by state.physicsTransform::scale
    override var velocity: Vec3f by state::velocity
    override var acceleration: Vec3f by state::acceleration
    override var affectedByGravity: Boolean by state::affectedByGravity
    override var mass: Float by  state::mass
    override var isImmovable: Boolean by state::isImmovable
    override var collisionGroup: Int by state::collisionGroup
    override var position by state::position
    override var orientation by state::orientation

    override fun cleanup() {
        physics.remove(this)
    }
}
