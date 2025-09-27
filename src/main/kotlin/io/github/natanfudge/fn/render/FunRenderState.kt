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
import io.github.natanfudge.wgpu4k.matrix.Mat4f


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

    override var transform: Mat4f = parentTransform.transform.mul(localTransform.transform)
        private set


    init {
        // These 2 are called earliest so the transform field will be updated in time for its usage in this class's override of afterTransformChange.
        localTransform.afterTransformChange {
            transform = parentTransform.transform.mul(it)
        }.closeWithThis()
        parentTransform.afterTransformChange {
            transform = it.mul(localTransform.transform)
        }.closeWithThis()
        afterTransformChange {
            updateTransform(it)
        }.closeWithThis()
    }

    override fun afterTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f> {
        val localListener = localTransform.afterTransformChange {
            callback(transform)
        }
        val parentListener = parentTransform.afterTransformChange {
            callback(transform)
        }
        return localListener.compose(parentListener).cast()
    }

    var baseAABB by funValue(getAxisAlignedBoundingBox(model.mesh)) {
        beforeChange {
            boundingBox = it.transformed(transform)
        }
    }


    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(transform)
        private set

    var tint: Tint by funValue<Tint>(Tint(Color.White, 0f)) {
        beforeChange {
            checkConsistentDespawnStatus()
            if (tint != it && !despawned) {
                renderInstance.setTintColor(it.color)
                renderInstance.setTintStrength(it.strength)
            }
        }
    }

    val renderInstance: RenderInstance = renderer.spawn(
        id, renderer.getOrBindModel(model), this, parentTransform.transform, tint
    )


    fun setTexture(image: FunImage) {
        renderInstance.model.setTexture(image)
    }

    private fun checkConsistentDespawnStatus() {
        check(despawned == renderInstance.despawned) { "Despawn status of FunRenderState '$id' does not match despawn status of its RenderInstance ($despawned != ${renderInstance.despawned})" }
    }


    private fun updateTransform(transform: Mat4f) {
        checkConsistentDespawnStatus()
        if (despawned) return
        this.boundingBox = baseAABB.transformed(transform)
        renderInstance.setTransform(transform)
    }


    fun joint(name: String): Transformable {
        val nodeId = model.nodeHierarchy.find { it.name == name }?.id ?: throw IllegalArgumentException(
            "No joint with name $name (actual: ${
                model.nodeHierarchy.toList().map { it.name }
            })"
        )
        return renderInstance.jointTransform(this, nodeId).closeWithThis()
    }

    var despawned = false

    override fun cleanup() {
        despawned = true
        renderInstance.close()
    }
}


class SimpleRenderObject(id: FunId, model: Model) : Fun(id) {
    val render by render(model)
}

class SimplePhysicsObject(id: FunId, model: Model, physicsSystem: PhysicsSystem) : Fun(id) {
    val physics = physics(physicsSystem)
    val render by render(model, physics)
}