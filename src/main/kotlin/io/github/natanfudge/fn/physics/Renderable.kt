package io.github.natanfudge.fn.physics

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.base.PhysicsMod
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

interface Renderable : Boundable {
    val transform: Mat4f
    var baseAABB: AxisAlignedBoundingBox

    override val boundingBox: AxisAlignedBoundingBox
}

interface Taggable {
    fun <T> getTag(tag: Tag<T>): T?
    fun <T> setTag(tag: Tag<T>, value: T?)
    fun removeTag(tag: Tag<*>)
    fun hasTag(tag: Tag<*>): Boolean
}

data class Tag<T>(val name: String)

interface MutableTransform {
    var position: Vec3f
    var rotation: Quatf
    var scale: Vec3f
}

interface RenderObject : MutableTransform {
    var tint: Tint
}

interface Visible : Renderable, RenderObject, Taggable

fun Fun.renderState(model: Model, name: String = "render") = FunRenderState(name, this, model)
fun Fun.physics(
    renderState: FunRenderState,
    physics: PhysicsSystem,
) = FunPhysics(this, renderState, physics)

fun Fun.physics(
    renderState: FunRenderState,
    physics: PhysicsMod,
) = FunPhysics(this, renderState, physics.system)


class FunRenderState(
    name: String,
    parent: Fun,
//    id: FunId,
//    app: FunApp,
    val model: Model,
) : Visible, Fun(parent, name) {
    override val data: Any? = parent


    val baseAABBState = funValue(getAxisAlignedBoundingBox(model.mesh), "baseAABB")

    override var baseAABB: AxisAlignedBoundingBox by baseAABBState


    val positionState = funValue<Vec3f>(model.initialTransform.position, "translation") {
        updateMatrix(position = it)
    }
    val rotationState = funValue<Quatf>(model.initialTransform.rotation, "rotation") {
        updateMatrix(orientation = it)

    }
    val scaleState = funValue<Vec3f>(model.initialTransform.scale, "scaling") {
        updateMatrix(scale = it)
    }

    val tintState: ClientFunValue<Tint> = funValue<Tint>(Tint(Color.White, 0f), "tint") {
//        check(!despawned) { "Attempt to change tint of a despawned object '$id'" }
        if (tint != it && !despawned) {
            renderInstance.setTintColor(it.color)
            renderInstance.setTintStrength(it.strength)
        }
    }

    /**
     * For `Fun` that have physics, it's generally best to update the position with the physics object, since those are only applied once each frame.
     */
    override var position by positionState

    /**
     * For `Fun` that have physics, it's generally best to update the rotation with the physics object, since those are only applied once each frame.
     */
    override var rotation by rotationState
    override var scale by scaleState
    override var tint by tintState

    /**
     * This value should be reassigned if you want Fun to react to changes in it.
     */
    final override var transform: Mat4f = Mat4f.translateRotateScale(position, rotation, scale)
        private set


    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(transform)
        private set

    val renderInstance: RenderInstance = context.world.getOrBindModel(model).spawn(id, this, tint)


    var despawned = false

    override fun cleanup() {
        despawned = true
        renderInstance.despawn()
    }

    private fun updateMatrix(position: Vec3f = this.position, orientation: Quatf = this.rotation, scale: Vec3f = this.scale) {
//        check(!despawned) { "Attempt to change transform of a despawned object" }
        if (despawned) return
        val matrix = Mat4f.translateRotateScale(position, orientation, scale)
        this.transform = matrix
        this.boundingBox = baseAABB.transformed(matrix)
        renderInstance.setTransform(matrix)
    }
}


class SimpleRenderObject(id: FunId, context: FunContext, model: Model) : Fun(id, context) {
    val render = renderState(model)
}

class SimplePhysicsObject(id: FunId, context: FunContext, model: Model, physics: PhysicsSystem) : Fun(id, context) {
    val render = renderState(model)
    val physics = physics(render, physics)
}

class FunPhysics(
//    id: FunId,
//    context: FunContext,
    parent: Fun,
    private val renderState: FunRenderState,
    private val physics: PhysicsSystem,
) : Fun(parent, "physics"), Body {

//    override val boundingBox: AxisAlignedBoundingBox get() = renderState.boundingBox

    // SUS: It pains me to hold this variable and update it whenever position changes, but it's the simplest way for now.
    // The better way is to directly recalculate the bounding box based on the current rotation and position, and the baseAABB of the renderState.
//    private var transform = renderState.transform.copy()

    override var boundingBox: AxisAlignedBoundingBox = renderState.boundingBox
        private set

    /**
     * These values are changed often by the physics system, so we only want the UI to update when the physics system commits changes.
     */
    override var position: Vec3f = renderState.position.copy()
        set(value) {
            field = value
            // Position affects aabb
            updateAABB()
        }
    override var velocity: Vec3f = Vec3f.zero()
    override var acceleration: Vec3f = Vec3f.zero()
    override var rotation = renderState.rotation.copy()
        set(value) {
            field = value
            // Rotation affects aabb
            updateAABB()
        }
    override var angularVelocity: Vec3f = Vec3f.zero()
    override var isGrounded: Boolean = true


//        get() = renderState.position
//        set(value) {
//            if (value != renderState.position) {
//                println("Setting new value: from $value to ${renderState.position}")
//            }
//            renderState.position = value
//        }

    /**
     * Values exposed to the visual editor.
     */
    private var _velocity: Vec3f by funValue(velocity, "velocity") {
        velocity = it
    }
    private var _acceleration: Vec3f by funValue(acceleration, "acceleration") {
        acceleration = it

    }
    private var _angularVelocity: Vec3f by funValue(angularVelocity, "angularVelocity") {
        angularVelocity = it
    }

    private var _isGrounded: Boolean by funValue(isGrounded, "isGrounded") {
        isGrounded = it
    }

    private val positionListener = renderState.positionState.onChange {
        position = it
        // Transform changed -> new transform matrix -> new aabb
        updateAABB()
    }

    private val rotationListener = renderState.rotationState.onChange {
        rotation = it
        // Transform changed -> new transform matrix -> new aabb
        updateAABB()
    }

    private val scaleListener = renderState.scaleState.onChange {
        // Scale change -> calculate new AABB
        updateAABB(newScale = it)
    }

    private val bbListener = renderState.baseAABBState.onChange {
        // BaseAABB changed -> new aabb
        boundingBox = it.transformed(calculateTransformMatrix())
    }

    private fun calculateTransformMatrix(newScale: Vec3f = renderState.scale): Mat4f {
        return Mat4f.translateRotateScale(this.position, this.rotation, newScale)
    }


    private fun updateAABB(newScale: Vec3f = renderState.scale) {
        val newMatrix = calculateTransformMatrix(newScale)
        boundingBox = renderState.baseAABB.transformed(newMatrix)
    }

    override var affectedByGravity: Boolean by funValue(true, "affectedByGravity")
    override var mass: Float by funValue(1f, "mass")
    override var isImmovable: Boolean by funValue(false, "isImmovable")


    /**
     * Apply changes from the physics system -> to the UI.
     */
    override fun commit() {
        if (_velocity != velocity) {
            _velocity = velocity
        }
        if (_acceleration != acceleration) {
            _acceleration = acceleration
        }
        if (_angularVelocity != angularVelocity) {
            _angularVelocity = angularVelocity
        }
        if (_isGrounded != isGrounded) {
            _isGrounded = isGrounded
        }
        if (renderState.position != position) {
            renderState.position = position
        }
        if (renderState.rotation != rotation) {
            renderState.rotation = rotation
        }
    }


//    fun getTouching(): List<Fun> {
//        return physics.getTouching(this).map {
//            check(it is FunPhysics) { "Only expecting FunPhysics to be inserted to the physics system" }
//            it.parent!!
//        }
//    }


    init {
        physics.add(this)
    }

    override fun cleanup() {
        physics.remove(this)
        // Close listeners to outside state
        closeAll(positionListener, rotationListener, bbListener, scaleListener)
    }
}

