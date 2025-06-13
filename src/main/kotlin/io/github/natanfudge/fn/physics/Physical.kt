package io.github.natanfudge.fn.physics

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Boundable
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.getAxisAlignedBoundingBox
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

interface Physical : Boundable {
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
    translate: Vec3f = Vec3f.zero(),
    rotate: Quatf = Quatf.identity(),
    scale: Vec3f = Vec3f(1f, 1f, 1f),
//    transform: Mat4f = Mat4f.identity(),
    color: Color = Color.White,
    baseAABB: AxisAlignedBoundingBox = getAxisAlignedBoundingBox(model.mesh)
) : Physical, Fun(id, context) {
    override var baseAABB: AxisAlignedBoundingBox by funValue(baseAABB)

    var translate: Vec3f by funValue(translate) {
        updateMatrix()
    }
    var rotate: Quatf by funValue(rotate) {
        updateMatrix()
    }
    var scale: Vec3f by funValue(scale) {
        updateMatrix()
    }

    var color: Color by funValue(color) {
        renderInstance.setColor(it)
    }

    var hasDespawned = false

    private fun updateMatrix() {
        val matrix = buildMatrix()
        this._transform = matrix
        renderInstance.setTransform(matrix)
    }

    private fun buildMatrix(): Mat4f {
        return Mat4f.translateRotateScale(translate, rotate, scale)
    }

    private var _transform = buildMatrix()

    /**
     * This value should be reassigned if you want Fun to react to changes in it.
     */
    override val transform: Mat4f get() = _transform
    val renderInstance = context.getOrBindModel(model).getOrSpawn(id, this, color)


    override fun close() {
        hasDespawned = true
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