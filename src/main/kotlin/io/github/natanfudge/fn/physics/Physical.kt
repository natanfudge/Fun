package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.FunStateContext
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.wgpu4k.matrix.Mat4f

interface Physical {
    var transform: Mat4f
    var baseAABB: AxisAlignedBoundingBox
}


//abstract class AbstractPhysical: Physical

open class PhysicalFun(
    id: FunId,
    context: FunStateContext,
) : Physical, Fun(id, context) {
    override var baseAABB: AxisAlignedBoundingBox by funValue(AxisAlignedBoundingBox.UnitAABB) {

    }
    override var transform: Mat4f by funValue(Mat4f.identity())
}


//TODO: think about spawning/despawning in relation to state, and how RenderInstance is tied in with this.
// I'm thinking we could have state automatically managed that, and that renderinstance is not needed because we do everything by ID.
// but tbh RenderInstance is mostly a performance thing, so we don't need to do through a map every time. I think performance should def be skipped atm.


class X(context: FunStateContext) : PhysicalFun("X", context) {

}