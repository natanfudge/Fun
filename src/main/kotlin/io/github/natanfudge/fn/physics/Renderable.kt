package io.github.natanfudge.fn.physics

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

interface Renderable : Boundable {
    val transform: Mat4f
    var baseAABB: AxisAlignedBoundingBox

    override val boundingBox: AxisAlignedBoundingBox
        /// SLOW: this should be cached per Physical, only calculate it when baseAABB/transform changes.
        // FUTURE: We also need to update the ray tracing tree when baseAABB/transform changes.
        get() = baseAABB.transformed(transform)
}

interface Transform {
    var position: Vec3f
    var rotation: Quatf
    var scale: Vec3f
}

interface RenderObject : Transform {
    var tint: Tint
}

interface Visible : Renderable, RenderObject

fun Fun.renderState(model: Model) = FunRenderState(this, model)
fun Fun.physics(
    renderState: Visible,
    physics: PhysicsSystem,
) = FunPhysics(this, renderState, physics)

class FunRenderState(
    parent: Fun,
//    id: FunId,
//    app: FunApp,
    val model: Model,
) : Visible, Fun(parent, "render") {
    override val data: Any? = parent

    override var baseAABB: AxisAlignedBoundingBox by funValue(getAxisAlignedBoundingBox(model.mesh), "baseAABB", this)

    override var position by funValue<Vec3f>(Vec3f.zero(), "translation", this).apply {
        change.listen {
            updateMatrix(position = it)
        }
    }

    override var rotation by funValue<Quatf>(Quatf.identity(), "rotation", this).apply {
        change.listen {
            updateMatrix(orientation = it)
        }
    }
    override var scale by funValue<Vec3f>(Vec3f(1f, 1f, 1f), "scaling", this).apply {
        change.listen {
            updateMatrix(scale = it)
        }
    }
    override var tint by funValue<Tint>(Tint(Color.White, 0f), "tint", this).apply {
        change.listen {
            if (!despawned && value != it) {
                renderInstance.setTintColor(it.color)
                renderInstance.setTintStrength(it.strength)
            }
        }
    }

    /**
     * This value should be reassigned if you want Fun to react to changes in it.
     */
    final override var transform: Mat4f = Mat4f.translateRotateScale(position, rotation, scale)
        private set

    val renderInstance: RenderInstance = context.world.getOrBindModel(model).getOrSpawn(id, this, tint)


    var despawned = false

    override fun cleanup() {
        despawned = true
        renderInstance.despawn()
    }

    private fun updateMatrix(position: Vec3f = this.position, orientation: Quatf = this.rotation, scale: Vec3f = this.scale) {
        if (!despawned) {
            val matrix = Mat4f.translateRotateScale(position, orientation, scale)
            this.transform = matrix
            renderInstance.setTransform(matrix)
        }
    }
}


class FunPhysics(
//    id: FunId,
//    context: FunContext,
    parent: Fun,
    private val renderState: Visible,
    private val physics: PhysicsSystem,
) : Fun(parent, "physics"), Body {
    override val boundingBox: AxisAlignedBoundingBox get() = renderState.boundingBox
    override var position: Vec3f
        get() = renderState.position
        set(value) {
            renderState.position = value
        }
    override var velocity: Vec3f by funValue(Vec3f.zero(), "velocity", this)
    override var acceleration: Vec3f by funValue(Vec3f.zero(), "acceleration", this)
    override var affectedByGravity: Boolean by funValue(true, "affectedByGravity", this)

    init {
        physics.add(this)
    }

    override fun cleanup() {
        physics.remove(this)
    }
}

//open class PhysicalFun(
//    id: FunId,
//    context: FunContext,
//    private val physics: PhysicsSystem,
//    //SUS: overreaching boundary here - Physical is common and BoundModel is client. Need to abstract this somehow.
//    val model: Model,
//) : Renderable, Fun(id, context), Body {
////    override val data: Any? = this
//
//    private val _context = context
//
//    override var baseAABB: AxisAlignedBoundingBox by funValue(getAxisAlignedBoundingBox(model.mesh), "baseAABB", this)
//
//    override var position by funValue<Vec3f>(Vec3f.zero(), "translation", this).apply {
//        change.listen {
//            updateMatrix(position = it)
//        }
//    }
//
//
//    var rotation by funValue<Quatf>(Quatf.identity(), "rotation", this).apply {
//        change.listen {
//            updateMatrix(orientation = it)
//        }
//    }
//    var scale by funValue<Vec3f>(Vec3f(1f, 1f, 1f), "scaling", this).apply {
//        change.listen {
//            updateMatrix(scale = it)
//        }
//    }
//    var tint by funValue<Tint>(Tint(Color.White, 0f), "tint", this).apply {
//        change.listen {
//            if (!despawned && value != it) {
//                renderInstance.setTintColor(it.color)
//                renderInstance.setTintStrength(it.strength)
//            }
//        }
//    }
//
//    /**
//     * This value should be reassigned if you want Fun to react to changes in it.
//     */
//    final override var transform: Mat4f = Mat4f.translateRotateScale(position, rotation, scale)
//        private set
//
//    override var velocity: Vec3f by funValue(Vec3f.zero(), "velocity", this)
//    override var acceleration: Vec3f by funValue(Vec3f.zero(), "acceleration", this)
//    override var affectedByGravity: Boolean by funValue(true, "affectedByGravity", this)
//
//    override val boundingBox: AxisAlignedBoundingBox
//        get() = super.boundingBox
//
//    fun copy(id: FunId) = PhysicalFun(id, _context, physics, model).let {
//        it.baseAABB = baseAABB
//        it.position = position
//        it.rotation = rotation
//        it.scale = scale
//        it.tint = tint
//        it.velocity = velocity
//        it.acceleration = acceleration
//        it.affectedByGravity = affectedByGravity
//        it.transform = transform
//    }
//
//    val renderInstance: RenderInstance = context.world.getOrBindModel(model).getOrSpawn(id, this, tint)
//
//
//    var despawned = false
//
//    init {
//        physics.add(this)
//    }
//
//
//    private fun updateMatrix(position: Vec3f = this.position, orientation: Quatf = this.rotation, scale: Vec3f = this.scale) {
//        if (!despawned) {
//            val matrix = Mat4f.translateRotateScale(position, orientation, scale)
//            this.transform = matrix
//            renderInstance.setTransform(matrix)
//        }
//    }
//
//    override fun close() {
//        despawned = true
//        super.close()
//        renderInstance.despawn()
//        physics.remove(this)
//    }
//}


//TODO: think about spawning/despawning in relation to state, and how RenderInstance is tied in with this.
// I'm thinking we could have state automatically managed that, and that renderinstance is not needed because we do everything by ID.
// but tbh RenderInstance is mostly a performance thing, so we don't need to do through a map every time. I think performance should def be skipped atm.


//class X(context: FunStateContext) : PhysicalFun("X", context) {
//
//}