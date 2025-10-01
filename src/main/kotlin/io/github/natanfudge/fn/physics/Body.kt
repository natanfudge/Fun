package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.wgpu4k.matrix.Mat4f
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
    var orientation: Quatf
    var angularVelocity: Vec3f
    var mass: Float

    val affectedByGravity: Boolean
    val isImmovable: Boolean

    var collisionGroup: Int

    var isGrounded: Boolean // SLOW: Don't think we need this for EVERYTHING

}

class SimpleBody(
    override var position: Vec3f,
    override var velocity: Vec3f = Vec3f.zero(),
    override var acceleration: Vec3f = Vec3f.zero(),
    val baseAABB: AxisAlignedBoundingBox = AxisAlignedBoundingBox.UnitAABB,
    val scale: Vec3f,
    override val affectedByGravity: Boolean = true,
    override var orientation: Quatf = Quatf.identity(),
    override var angularVelocity: Vec3f = Vec3f.zero(),
    override var mass: Float = 1f,
    override val isImmovable: Boolean = false,
    override var isGrounded: Boolean = false,
    override var collisionGroup: Int = 0
) : Body {
    override val boundingBox: AxisAlignedBoundingBox get() = baseAABB.transformed(
        Mat4f.translateRotateScale(position, orientation, scale)
    )

}
