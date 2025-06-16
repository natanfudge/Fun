package io.github.natanfudge.fn.physics

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunId
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

//class GiveMeYourNameHack: ReadWriteProperty<Any?, Int> {
//    override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
//        TODO("Not yet implemented")
//    }
//
//    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
//        TODO("Not yet implemented")
//    }
//}

//class Transform(id: FunId,  context: FunContext, translation: Vec3f, rotation: Quatf, scale: Vec3f): Fun(id.child("transform"), context) {
//    val translation = funValue<Vec3f>(translation, "translation", this)
//    val rotation = funValue<Quatf>(rotation, "rotation", this)
//    val scale = funValue<Vec3f>(scale, "scale", this)
//}


// TODO: need to see how I resolve the infinite parmaeter issue...

open class PhysicalFun(
    id: FunId,
    context: FunContext,
    private val physics: PhysicsSystem,
    //SUS: overreaching boundary here - Physical is common and BoundModel is client. Need to abstract this somehow.
     val model: Model,
) : Renderable, Fun(id, context), Kinematic {

    private val _context = context

    override var baseAABB: AxisAlignedBoundingBox by funValue(getAxisAlignedBoundingBox(model.mesh), "baseAABB", this)

    override var position by funValue<Vec3f>(Vec3f.zero(), "translation", this).apply {
        change.listen {
            updateMatrix(position = it)
        }
    }


    var rotation by funValue<Quatf>(Quatf.identity(), "rotation", this).apply {
        change.listen {
            updateMatrix(orientation = it)
        }
    }
    var scale by funValue<Vec3f>(Vec3f(1f, 1f, 1f), "scaling", this).apply {
        change.listen {
            updateMatrix(scale = it)
        }
    }
    var tint by funValue<Tint>(Tint(Color.White, 0f), "tint", this).apply {
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

    override var velocity: Vec3f by funValue(Vec3f.zero(), "velocity", this)
    override var acceleration: Vec3f by funValue(Vec3f.zero(), "acceleration", this)
    override var affectedByGravity: Boolean by funValue(true, "affectedByGravity", this)

    override val boundingBox: AxisAlignedBoundingBox
        get() = super.boundingBox

    fun copy(id: FunId) = PhysicalFun(id, _context, physics, model).let {
        it.baseAABB = baseAABB
        it.position = position
        it.rotation = rotation
        it.scale = scale
        it.tint = tint
        it.velocity = velocity
        it.acceleration = acceleration
        it.affectedByGravity = affectedByGravity
        it.transform = transform
    }

    val renderInstance: RenderInstance = context.world.getOrBindModel(model).getOrSpawn(id, this, tint)


    var despawned = false

    init {
        physics.add(this)
    }


    private fun updateMatrix(position: Vec3f = this.position, orientation: Quatf = this.rotation, scale: Vec3f = this.scale) {
        if (!despawned) {
            val matrix = Mat4f.translateRotateScale(position, orientation, scale)
            this.transform = matrix
            renderInstance.setTransform(matrix)
        }
    }

    override fun close() {
        despawned = true
        super.close()
        renderInstance.despawn()
        physics.remove(this)
    }
}


//TODO: think about spawning/despawning in relation to state, and how RenderInstance is tied in with this.
// I'm thinking we could have state automatically managed that, and that renderinstance is not needed because we do everything by ID.
// but tbh RenderInstance is mostly a performance thing, so we don't need to do through a map every time. I think performance should def be skipped atm.


//class X(context: FunStateContext) : PhysicalFun("X", context) {
//
//}