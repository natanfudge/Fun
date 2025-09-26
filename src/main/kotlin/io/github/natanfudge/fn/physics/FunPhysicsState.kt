package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f

fun Fun.physics(
    physics: PhysicsSystem,
    baseAABB: AxisAlignedBoundingBox = AxisAlignedBoundingBox.UnitAABB,
) = FunPhysicsState(this, baseAABB, physics)

class FunPhysicsState(
    funParent: Fun,
    baseAABB: AxisAlignedBoundingBox,
    private val physics: PhysicsSystem,
) : Fun(funParent.id.child("physics"), funParent), Body, Transformable {
    /**
     * Stores the global transforms of this physics object.
     * Note that while in reality things do not become smaller or larger (modify the scale property), it makes sense
     * to make things "physically smaller" in a game and modify `physics.scale` (and not just "visually smaller" with `render.scale`).
     */
    private val transformState = FunTransform(this)

    override val transform: Mat4f get() = transformState.transform

    override fun afterTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f> {
        return transformState.afterTransformChange(callback)
    }

    override fun toString(): String {
        return "$id at $position"
    }


    val positionState = transformState.translationState
    override var position by transformState::translation

    /**
     * For `Fun` that have physics, it's generally best to update [orientation] and not [rotation], since those are only applied once each frame.
     */
    override var orientation by transformState::rotation

    var scale: Vec3f by transformState::scale

    var baseAABB by funValue(baseAABB)

    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(transform)
        private set


    private fun updateAABB(transform: Mat4f) {
        boundingBox = baseAABB.transformed(transform)
    }

    init {
        afterTransformChange {
            updateAABB(it)
        }.closeWithThis()
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

