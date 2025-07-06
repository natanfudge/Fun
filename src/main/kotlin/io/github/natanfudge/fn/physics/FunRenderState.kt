package io.github.natanfudge.fn.physics

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.utils.find
import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.*


fun Fun.render(model: Model, name: String = "render") = FunRenderState(name, this, RootTransformable, model)
fun Fun.render(model: Model, parent: Transformable, name: String = "render"): FunRenderState {
    val render = FunRenderState(name, this, parent, model)
    return render
}
fun Fun.render(model: Model, physics: FunPhysics, name: String = "render"): FunRenderState {
    val render = FunRenderState(name, this, physics, model)
    physics.baseAABB = render.baseAABB
    return render
}

fun Fun.physics(
    physics: PhysicsSystem,
    baseAABB: AxisAlignedBoundingBox = AxisAlignedBoundingBox.UnitAABB,
) = FunPhysics(this, baseAABB, physics)

class FunRenderState(
    name: String,
    parentFun: Fun,
    parentTransform: Transformable,
    val model: Model,
) : HierarchicalTransformable(parentTransform, parentFun, name), Boundable {

    var baseAABB by funValue(getAxisAlignedBoundingBox(model.mesh), "baseAABB") {
        this.boundingBox = it.transformed(transform.toMatrix())
    }

    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(transform.toMatrix())
        private set

    val tintState: ClientFunValue<Tint> = funValue<Tint>(Tint(Color.White, 0f), "tint") {
        if (tint != it && !despawned) {
            renderInstance.setTintColor(it.color)
            renderInstance.setTintStrength(it.strength)
        }
    }
    var tint by tintState

    val renderInstance: RenderInstance = context.world.getOrBindModel(model).spawn(
        id, this, initialTransform = parentTransform.transform.toMatrix(), tint
    )
    init {
        onTransformChange {
            updateTransform(it)
        }
    }

    private fun updateTransform(transform: Transform) {
        if (despawned) return
        val matrix = transform.toMatrix()
        this.boundingBox = baseAABB.transformed(matrix)
        renderInstance.setTransform(matrix)
    }


    fun joint(name: String): Transformable {
        val nodeId = model.nodeHierarchy.find { it.name == name }?.id ?: throw IllegalArgumentException("No joint with name $name (actual: ${model.nodeHierarchy.toList().map { it.name }})")
        return renderInstance.jointTransform(this, nodeId)
    }

    var despawned = false

    override fun cleanup() {
        despawned = true
        renderInstance.despawn()
    }
}


class SimpleRenderObject(id: FunId, context: FunContext, model: Model) : Fun(id, context) {
    val render = render(model)
}

class SimplePhysicsObject(id: FunId, context: FunContext, model: Model, physicsSystem: PhysicsSystem) : Fun(id, context) {
    val physics = physics(physicsSystem)
    val render = render(model, physics)
}

