package io.github.natanfudge.fn.physics

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.BoundModel
import io.github.natanfudge.fn.render.Boundable
import io.github.natanfudge.fn.render.getAxisAlignedBoundingBox
import io.github.natanfudge.wgpu4k.matrix.Mat4f

interface Physical : Boundable {
    var transform: Mat4f
    var baseAABB: AxisAlignedBoundingBox

    override val boundingBox: AxisAlignedBoundingBox
        /// SLOW: this should be cached per Physical, only calculate it when baseAABB/transform changes.
        // FUTURE: We also need to update the ray tracing tree when baseAABB/transform changes.
        get() = baseAABB.transformed(transform)
}


//abstract class AbstractPhysical: Physical

abstract class PhysicalFun(
    id: FunId,
    context: FunContext,
    //SUS: overreaching boundary here - Physical is common and BoundModel is client. Need to abstract this somehow.
    //TODO: I'm thinking again that this should accept a normal Model, and then its
    // conditionally bound here, using getOrBind on World, and then also instead of
    // spawn we have getOrSpawn so we can reuse the same underlying render stuff!
    model: BoundModel,
    transform: Mat4f = Mat4f.identity(),
    color: Color = Color.White,
    baseAABB: AxisAlignedBoundingBox = getAxisAlignedBoundingBox(model.model.mesh)
) : Physical, Fun(id, context) {
    override var baseAABB: AxisAlignedBoundingBox by funValue(baseAABB)

    /**
     * This value should be reassigned if you want Fun to react to changes in it.
     */
    override var transform: Mat4f by funValue(transform) {
        renderInstance.setTransform(it)
    }
    val renderInstance = model.spawn(this, color)

    fun despawn() {
        renderInstance.despawn()
    }
}


//TODO: think about spawning/despawning in relation to state, and how RenderInstance is tied in with this.
// I'm thinking we could have state automatically managed that, and that renderinstance is not needed because we do everything by ID.
// but tbh RenderInstance is mostly a performance thing, so we don't need to do through a map every time. I think performance should def be skipped atm.


//class X(context: FunStateContext) : PhysicalFun("X", context) {
//
//}