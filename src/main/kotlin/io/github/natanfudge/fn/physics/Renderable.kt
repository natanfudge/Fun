package io.github.natanfudge.fn.physics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Velocity
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
    override var baseAABB: AxisAlignedBoundingBox by funValue(baseAABB)

    override var position: Vec3f by funValue(position) { old, new ->
        updateMatrix()
    }
    var orientation: Quatf by funValue(orientation) { old, new ->
        updateMatrix()
    }

    override var velocity: Vec3f by funValue(velocity)
    override var acceleration: Vec3f by funValue(acceleration)
    override var affectedByGravity: Boolean by funValue(affectedByGravity)

    override val boundingBox: AxisAlignedBoundingBox
        get() = super<Renderable>.boundingBox


    var scale: Vec3f by funValue(scale) { old, new ->
        updateMatrix()
    }

    var tint: Tint by funValue(tint) { old, new ->
        if (!despawned) {
            if (old != new) {
                renderInstance.setTintColor(new.color)
                renderInstance.setTintStrength(new.strength)
            }
        }
    }

    var despawned = false

    private fun updateMatrix() {
        if (!despawned) {
            val matrix = buildMatrix()
            this._transform = matrix
            renderInstance.setTransform(matrix)
        }
    }

    private fun buildMatrix(): Mat4f {
        return Mat4f.translateRotateScale(this@PhysicalFun.position, this@PhysicalFun.orientation, scale)
    }

    private var _transform = buildMatrix()

    /**
     * This value should be reassigned if you want Fun to react to changes in it.
     */
    override val transform: Mat4f get() = _transform
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