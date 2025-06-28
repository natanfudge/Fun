package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class FunPhysics(
    funParent: Fun,
//    transformParent: Transformable,
//    parent: Transformable,
    baseAABB: AxisAlignedBoundingBox,
    private val physics: PhysicsSystem,
) : Fun(funParent, "physics"), Body, Transformable {

    private val transform = FunTransform(this)

    //TODO: having this public value that doesn't get updated asap is a big problem, we are using old values accidentally.
    // Optimally we can just use one value and avoid doing too much work if we don't need to.

    override val translation: Vec3f get() = transform.translation

    override val rotation: Quatf get() = transform.rotation

    override var scale: Vec3f
        get() = transform.scale
        set(value) {
            transform.scale = value
        }

    var baseAABB by funValue(baseAABB, "baseAABB") {
        this.boundingBox = it.transformed(calculateTransformMatrix())
    }

    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(calculateTransformMatrix())
        private set


    private fun updateAABB(translation: Vec3f = this.position, rotation: Quatf = this.rotation, scale: Vec3f = this.scale) {
        val newMatrix = Mat4f.translateRotateScale(translation, rotation, scale)
        boundingBox = baseAABB.transformed(newMatrix)
        val x = 2
    }

    init {
        onTranslationChanged {
            position = it
        }
        onRotationChanged {
            orientation = it
        }
        onScaleChanged {
            updateAABB(scale = it)
        }
    }

    //TODO: I should optimize WorldRender to only apply transform once per frame if invalidated, then i don't need commit() and the two extra variables
    // Optimization: only update translation and rotation of the FunTransform when we run commit(), because those updates can be quite slow
    // (for example we update the gpu transform)
    /**
     * For `Fun` that have physics, it's generally best to update [position] and not [translation], since those are only applied once each frame.
     */
    override var position: Vec3f = Vec3f()
        set(value) {
            updateAABB(translation = value)
            field = value
        }

    /**
     * For `Fun` that have physics, it's generally best to update [orientation] and not [rotation], since those are only applied once each frame.
     */
    override var orientation: Quatf = Quatf()
        set(value) {
            updateAABB(rotation = value)
            field = value
        }

    override var velocity: Vec3f by funValue(Vec3f.zero(), "velocity")
    override var acceleration: Vec3f by funValue(Vec3f.zero(), "acceleration")
    override var angularVelocity: Vec3f by funValue(Vec3f.zero(), "angularVelocity")
    override var isGrounded: Boolean by funValue(true, "isGrounded")
    override var affectedByGravity: Boolean by funValue(true, "affectedByGravity")
    override var mass: Float by funValue(1f, "mass")
    override var isImmovable: Boolean by funValue(false, "isImmovable")
    override var collisionGroup: Int by funValue(0, "collisionGroup")


    /**
     * Apply changes from the physics system -> to the UI.
     */
    override fun commit() {
        if (position != transform.translation) {
            transform.translation = position
        }
        if (orientation != transform.rotation) {
            transform.rotation = orientation
        }
    }

    init {
        physics.add(this)
    }

    override fun cleanup() {
        physics.remove(this)
    }


    override fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = transform.onTranslationChanged(callback)

    override fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf> = transform.onRotationChanged(callback)

    override fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = transform.onScaleChanged(callback)
}

