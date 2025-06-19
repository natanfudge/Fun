package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

/**
 * Important note: mutable [Vec3f] are going to be mutated AND reassigned
 */
interface Body {
    val boundingBox: AxisAlignedBoundingBox
    var position: Vec3f
    var velocity: Vec3f
    var acceleration: Vec3f
    var rotation: Quatf
    var angularVelocity: Vec3f
    var mass: Float

    val affectedByGravity: Boolean
    val isImmovable: Boolean

    /**
     * Apply the changes of state in the body to consumers of this body like renderers.
     */
    fun commit()
}

//class SimpleKinematic(
//    override var position: Vec3f,
//    override var velocity: Vec3f = Vec3f.zero(),
//    override var acceleration: Vec3f = Vec3f.zero(),
//    override val boundingBox: AxisAlignedBoundingBox = AxisAlignedBoundingBox.UnitAABB,
//    override val affectedByGravity: Boolean = true
//) : Body
