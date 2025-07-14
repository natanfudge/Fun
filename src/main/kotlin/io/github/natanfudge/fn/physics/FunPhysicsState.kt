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

    override var transform: Transform
        get() = transformState.transform
        set(value) {
            transformState.transform = value
        }

    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        return transformState.onTransformChange(callback)
    }


    //TODO: need for this to not emit events whenever physics updates this variable , because physics often return the variable to the original value.
    override var position by transformState::translation
    override fun commit() {
        transformState.translation = position
    }

    /**
     * For `Fun` that have physics, it's generally best to update [orientation] and not [rotation], since those are only applied once each frame.
     */
    override var orientation by transformState::rotation

    var scale: Vec3f by transformState::scale

    var baseAABB by funValue(baseAABB, "baseAABB", beforeChange = {
        this.boundingBox = it.transformed(transform.toMatrix())
    })

    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(transform.toMatrix())
        private set


    private fun updateAABB(transform: Transform) {
        boundingBox = baseAABB.transformed(transform.toMatrix())
        println("Bounding box set to $boundingBox (transformed from base of $baseAABB)")
    }

    init {
        onTransformChange {
            if (it != transform) {
                updateAABB(it)
//                // The position state is separate from the transform.translation state, so if the transform changes we update it here.
//                this.position = it.translation //TODO
            }
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

