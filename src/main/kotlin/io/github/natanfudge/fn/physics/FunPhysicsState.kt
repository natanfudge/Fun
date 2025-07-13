package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

fun Fun.physics(
    physics: PhysicsSystem,
    baseAABB: AxisAlignedBoundingBox = AxisAlignedBoundingBox.UnitAABB,
) = FunPhysicsState(this, baseAABB, physics)

class FunPhysicsState(
    funParent: Fun,
    baseAABB: AxisAlignedBoundingBox,
    private val physics: PhysicsSystem,
) : Fun(funParent, "physics"), Body, Transformable {
    private val physicsTransform = FunTransform(this)

    override var transform: Transform get() = physicsTransform.transform
        set(value) {
            physicsTransform.transform = value
        }

    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        return physicsTransform.onTransformChange(callback)
    }


    /**
     * For `Fun` that have physics, it's generally best to update [position] and not [translation], since those are only applied once each frame.
     */
    override var position by physicsTransform::translation

    /**
     * For `Fun` that have physics, it's generally best to update [orientation] and not [rotation], since those are only applied once each frame.
     */
    override var orientation by physicsTransform::rotation

    var scale: Vec3f by physicsTransform::scale

    var baseAABB by funValue(baseAABB, "baseAABB") {
        this.boundingBox = it.transformed(transform.toMatrix())
    }

    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(transform.toMatrix())
        private set


    private fun updateAABB(transform: Transform) {
        boundingBox = baseAABB.transformed(transform.toMatrix())
    }

    init {
        onTransformChange {
            updateAABB(it)
        }
    }

    override var velocity: Vec3f by funValue(Vec3f.zero(), "velocity")
    override var acceleration: Vec3f by funValue(Vec3f.zero(), "acceleration")
    override var angularVelocity: Vec3f by funValue(Vec3f.zero(), "angularVelocity")
    override var isGrounded: Boolean by funValue(false, "isGrounded")
    override var affectedByGravity: Boolean by funValue(true, "affectedByGravity")
    override var mass: Float by funValue(1f, "mass")
    override var isImmovable: Boolean by funValue(false, "isImmovable")
    override var collisionGroup: Int by funValue(0, "collisionGroup")

    init {
        physics.add(this)
    }

    override fun cleanup() {
        physics.remove(this)
    }

}

