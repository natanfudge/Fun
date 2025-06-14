package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

/**
 * Important note: mutable [Vec3f] are going to be mutated AND reassigned
 */
interface Kinematic {
    val boundingBox: AxisAlignedBoundingBox
    var position: Vec3f
    var velocity: Vec3f
    var acceleration: Vec3f
    val affectedByGravity: Boolean
}

class SimpleKinematic(
    override var position: Vec3f,
    override var velocity: Vec3f = Vec3f.zero(),
    override var acceleration: Vec3f = Vec3f.zero(),
    override val boundingBox: AxisAlignedBoundingBox = AxisAlignedBoundingBox.UnitAABB,
    override val affectedByGravity: Boolean = true
) : Kinematic
