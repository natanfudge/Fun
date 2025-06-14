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


open class PhysicalFun(
    id: FunId,
    context: FunContext,
    //SUS: overreaching boundary here - Physical is common and BoundModel is client. Need to abstract this somehow.
    model: Model,
    position: Vec3f = Vec3f.zero(),
    orientation: Quatf = Quatf.identity(),
    scale: Vec3f = Vec3f(1f, 1f, 1f),
    tint: Tint = Tint(Color.White, 0f),
    baseAABB: AxisAlignedBoundingBox = getAxisAlignedBoundingBox(model.mesh),
    affectedByGravity: Boolean = true,
    velocity: Vec3f = Vec3f.zero(),
    acceleration: Vec3f = Vec3f.zero(),
) : Renderable, Fun(id, context), Kinematic {
    //    val _baseAABB = funValue(baseAABB)
    override var baseAABB: AxisAlignedBoundingBox by funValue(baseAABB, "baseAABB", this)

//    val x = baseAABB::

    //    val positionVal =
    val translation = funValue<Vec3f>(position, "translation", this)
    val rotation = funValue<Quatf>(orientation, "rotation", this)
    val scale = funValue<Vec3f>(scale, "scaling", this)
    val tint = funValue<Tint>(tint, "tint", this)

    init {
        translation.change.listen {
            updateMatrix(position = it)
        }
        rotation.change.listen {
            updateMatrix(orientation = it)
        }
        this.scale.change.listen {
            updateMatrix(scale = it)
        }

        this.tint.change.listen {
            if (!despawned && this.tint.value != it) {
                renderInstance.setTintColor(it.color)
                renderInstance.setTintStrength(it.strength)
            }
        }
    }

    override var position: Vec3f
        get() = translation.value
        set(value) {
            translation.value = value
        }
//    var orientation: Quatf by funValue(orientation) { old, new ->
//        updateMatrix(orientation = new)
//    }

    override var velocity: Vec3f by funValue(velocity, "velocity", this)
    override var acceleration: Vec3f by funValue(acceleration, "acceleration", this)
    override var affectedByGravity: Boolean by funValue(affectedByGravity, "affectedByGravity", this)

    override val boundingBox: AxisAlignedBoundingBox
        get() = super<Renderable>.boundingBox


//    var scale: Vec3f by funValue(scale) { old, new ->
//        updateMatrix(scale = new)
//    }


    var despawned = false

    private fun updateMatrix(position: Vec3f = this.position, orientation: Quatf = this.rotation.value, scale: Vec3f = this.scale.value) {
        if (!despawned) {
            val matrix = Mat4f.translateRotateScale(position, orientation, scale)
            this.transform = matrix
            renderInstance.setTransform(matrix)
        }
    }

//    private fun buildMatrix(position: Vec3f, orientation: Quatf, scale: Vec3f): Mat4f {
//        return Mat4f.translateRotateScale(this@PhysicalFun.position, this@PhysicalFun.orientation, scale)
//    }

//    private var _transform = buildMatrix()

    /**
     * This value should be reassigned if you want Fun to react to changes in it.
     */
    final override var transform: Mat4f =  Mat4f.translateRotateScale(position, orientation, scale)
        private set
    val renderInstance = context.world.getOrBindModel(model).getOrSpawn(id, this, tint)


    override fun close() {
        despawned = true
        super.close()
        renderInstance.despawn()
    }
}


//TODO: think about spawning/despawning in relation to state, and how RenderInstance is tied in with this.
// I'm thinking we could have state automatically managed that, and that renderinstance is not needed because we do everything by ID.
// but tbh RenderInstance is mostly a performance thing, so we don't need to do through a map every time. I think performance should def be skipped atm.


//class X(context: FunStateContext) : PhysicalFun("X", context) {
//
//}