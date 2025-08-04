package io.github.natanfudge.fn.render

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.utils.find
import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.physics.*
import io.github.natanfudge.fn.util.*


fun Fun.render(model: Model, name: String, parent: Transformable = RootTransformable): FunRenderState {
    val render = FunRenderState(name, this, parent, model)
    return render
}

fun Fun.render(model: Model, physics: FunPhysicsState, name: String): FunRenderState {
    val render = FunRenderState(name, this, physics, model)
    physics.baseAABB = render.baseAABB
    return render
}


fun Fun.render(model: Model, parent: Transformable = RootTransformable): Delegate<FunRenderState> = obtainPropertyName {
    render(model, it, parent)
}

fun Fun.render(model: Model, physics: FunPhysicsState): Delegate<FunRenderState> = obtainPropertyName {
    render(model, physics, it)
}


class FunRenderState(
    name: String,
    parentFun: Fun,
    val parentTransform: Transformable,
    val model: Model,
) : Fun(parentFun.id.child(name), parentFun), Boundable, Transformable {

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

    var baseAABB by funValue(getAxisAlignedBoundingBox(model.mesh)){
        beforeChange {
            boundingBox = it.transformed(transform.toMatrix())
        }
    }


    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(transform.toMatrix())
        private set

    var tint: Tint by funValue<Tint>(Tint(Color.White, 0f)){
        beforeChange {
            if (tint != it && !despawned) {
                renderInstance.setTintColor(it.color)
                renderInstance.setTintStrength(it.strength)
            }
        }
    }

    val renderInstance: RenderInstance = context.world.spawn(
        id, context.world.getOrBindModel(model), this, parentTransform.transform.toMatrix(), tint
    )


    fun setTexture(image: FunImage) {
        renderInstance.model.setTexture(image)
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
        val nodeId = model.nodeHierarchy.find { it.name == name }?.id ?: throw IllegalArgumentException(
            "No joint with name $name (actual: ${
                model.nodeHierarchy.toList().map { it.name }
            })"
        )
        return renderInstance.jointTransform(this, nodeId)
    }

    var despawned = false

    override fun cleanup() {
        despawned = true
        renderInstance.close()
//        context.world.remove(renderInstance)
    }
}


class SimpleRenderObject(id: FunId, model: Model) : Fun(id) {
    val render by render(model)
}

class SimplePhysicsObject(id: FunId, model: Model, physicsSystem: PhysicsSystem) : Fun(id) {
    val physics = physics(physicsSystem)
    val render by render(model, physics)
}