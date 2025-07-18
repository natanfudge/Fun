package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Listener
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
    private val transformState = FunTransform(this)

    override var transform: Transform by transformState::transform

    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        return transformState.onTransformChange(callback)
    }

    override fun toString(): String {
        return "$id at ${transform.translation}"
    }


    override var position by transformState::translation

    /**
     * For `Fun` that have physics, it's generally best to update [orientation] and not [rotation], since those are only applied once each frame.
     */
    override var orientation by transformState::rotation

    var scale: Vec3f by transformState::scale

    var baseAABB by funValue(baseAABB)

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

    override var velocity: Vec3f by funValue(Vec3f.zero())
    override var acceleration: Vec3f by funValue(Vec3f.zero())
    override var angularVelocity: Vec3f by funValue(Vec3f.zero())
    override var isGrounded: Boolean by funValue(false)
    override var affectedByGravity: Boolean by funValue(true)
    override var mass: Float by funValue(1f)
    override var isImmovable: Boolean by funValue(false)
    override var collisionGroup: Int by funValue(0)

    init {
        physics.add(this)
    }

    override fun cleanup() {
        physics.remove(this)
    }

}

