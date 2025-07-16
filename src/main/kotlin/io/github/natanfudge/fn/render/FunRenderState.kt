package io.github.natanfudge.fn.render

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.utils.find
import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.physics.FunPhysicsState
import io.github.natanfudge.fn.physics.FunTransform
import io.github.natanfudge.fn.physics.PhysicsSystem
import io.github.natanfudge.fn.physics.RootTransformable
import io.github.natanfudge.fn.physics.Transformable
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.cast
import io.github.natanfudge.fn.util.compose


fun Fun.render(model: Model, name: String = "render") = FunRenderState(name, this, RootTransformable, model)
fun Fun.render(model: Model, parent: Transformable, name: String = "render"): FunRenderState {
    val render = FunRenderState(name, this, parent, model)
    return render
}
fun Fun.render(model: Model, physics: FunPhysicsState, name: String = "render"): FunRenderState {
    val render = FunRenderState(name, this, physics, model)
    physics.baseAABB = render.baseAABB
    return render
}


class FunRenderState(
    name: String,
    parentFun: Fun,
    val parentTransform: Transformable,
    val model: Model,
) : Fun(parentFun, name), Boundable, Transformable {

    val localTransform = FunTransform(this)

    override var transform: Transform = parentTransform.transform.mul(localTransform.transform)

    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        val localListener = localTransform.onTransformChange {
            transform = parentTransform.transform.mul(it)
            callback(transform)
        }
        val parentListener = parentTransform.onTransformChange {
            transform = it.mul(localTransform.transform)
            callback(transform)
        }
        return localListener.compose(parentListener).cast()
    }

    var baseAABB by funValue(getAxisAlignedBoundingBox(model.mesh), "baseAABB", beforeChange = {
        this.boundingBox = it.transformed(transform.toMatrix())
    })


    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(transform.toMatrix())
        private set

    val tintState: ClientFunValue<Tint> = funValue<Tint>(Tint(Color.White, 0f), "tint", beforeChange = {
        if (tint != it && !despawned) {
            renderInstance.setTintColor(it.color)
            renderInstance.setTintStrength(it.strength)
        }
    })
    var tint by tintState

    val renderInstance: RenderInstance = context.world.getOrBindModel(model).spawn(
        id, this, initialTransform = parentTransform.transform.toMatrix(), tint
    )

    fun setTexture(image: FunImage) {
        renderInstance.setTexture(image)
    }

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


class SimpleRenderObject(id: FunId, model: Model) : Fun(id) {
    val render = render(model)
}

class SimplePhysicsObject(id: FunId, model: Model, physicsSystem: PhysicsSystem) : Fun( id) {
    val physics = physics(physicsSystem)
    val render = render(model, physics)
}