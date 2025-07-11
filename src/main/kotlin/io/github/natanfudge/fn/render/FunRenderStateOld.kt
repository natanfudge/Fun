package io.github.natanfudge.fn.render

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.utils.find
import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.physics.*
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.cast
import io.github.natanfudge.fn.util.compose


fun FunOld.render(model: Model, name: String = "render", renderState: FunRenderState = FunRenderState(id.child(name))) =
    FunRenderObject(name, this, model, RootTransformable, renderState)

fun FunOld.render(model: Model, parent: Transformable, name: String = "render", renderState: FunRenderState = FunRenderState(id.child(name))): FunRenderObject {
    return FunRenderObject(name, this, model, parent, renderState)
//    val render = FunRenderStateOld(name, this, parent, model)
//    return render
}

fun FunOld.render(
    model: Model,
    physics: FunPhysicsState,
    name: String = "render",
    renderState: FunRenderState = FunRenderState(id.child(name)),
): FunRenderObject {
    val render = FunRenderObject(name, this, model, physics, renderState)
    physics.baseAABB = render.baseAABB()
    return render
}

fun FunOld.physics(
    physics: PhysicsSystem,
    baseAABB: AxisAlignedBoundingBox = AxisAlignedBoundingBox.UnitAABB,
    name: String = "physics",
    state: FunPhysicsState = FunPhysicsState(id.child(name)),
) = FunBody(this, physics, state).apply { state.baseAABB = baseAABB }

//class FunRenderState(
//    val name: String,
//    val parent: Fun
//)

//class FunRenderState(
//    val name: String,
//    val parentFun: String
//) {
//    val transform = FunTransform()
//}

class FunRender(state: FunTransformOld, val parentTransform: Transformable, val model: Model)

//@Serializable class FunRenderState(
//    val name: String,
//    val parentFun: FunId
//)

//@Deprecated("Use FunRenderObject")
//class FunRenderStateOld(
//    name: String,
//    parentFun: FunOld,
//    val parentTransform: Transformable,
//    val model: Model,
//) : FunOld(parentFun, name), Boundable, Transformable {
//
//    val localTransform = FunTransformOld(this)
//
//    override var transform: Transform = parentTransform.transform.mul(localTransform.transform)
//
//    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
//        val localListener = localTransform.onTransformChange {
//            transform = parentTransform.transform.mul(it)
//            callback(transform)
//        }
//        val parentListener = parentTransform.onTransformChange {
//            transform = it.mul(localTransform.transform)
//            callback(transform)
//        }
//        return localListener.compose(parentListener).cast()
//    }
//
//    var baseAABB by funValue(getAxisAlignedBoundingBox(model.mesh), "baseAABB") {
//        this.boundingBox = it.transformed(transform.toMatrix())
//    }
//
//    override var boundingBox: AxisAlignedBoundingBox = baseAABB.transformed(transform.toMatrix())
//        private set
//
//    val tintState: ClientFunValue<Tint> = funValue<Tint>(Tint(Color.White, 0f), "tint") {
//        if (tint != it && !despawned) {
//            renderInstance.setTintColor(it.color)
//            renderInstance.setTintStrength(it.strength)
//        }
//    }
//    var tint by tintState
//
//    val renderInstance: RenderInstance = context.world.getOrBindModel(model).spawn(
//        id, this, initialTransform = parentTransform.transform.toMatrix(), tint
//    )
//
//    init {
//        onTransformChange {
//            updateTransform(it)
//        }
//    }
//
//    private fun updateTransform(transform: Transform) {
//        if (despawned) return
//        val matrix = transform.toMatrix()
//        this.boundingBox = baseAABB.transformed(matrix)
//        renderInstance.setTransform(matrix)
//    }
//
//
//    fun joint(name: String): Transformable {
//        val nodeId = model.nodeHierarchy.find { it.name == name }?.id ?: throw IllegalArgumentException(
//            "No joint with name $name (actual: ${
//                model.nodeHierarchy.toList().map { it.name }
//            })"
//        )
//        return renderInstance.jointTransform(this, nodeId)
//    }
//
//    var despawned = false
//
//    override fun cleanup() {
//        despawned = true
//        renderInstance.despawn()
//    }
//}

class FunRenderState(override val id: FunId) : Fun() {
    val localTransform = FunTransform(child("transform"))

    var baseAABB: AxisAlignedBoundingBox? by funValue(null, "baseAABB")

    var tint by funValue<Tint>(Tint(Color.White, 0f), "tint")
}

class FunRenderObject(
    name: String,
    parentFun: FunOld, val model: Model, val parentTransform: Transformable, val state: FunRenderState,
) : Boundable, Transformable, AutoCloseable, FunOld(parentFun, "placeholder-$name")  //TODO: FunOld should not deal with fun ids
{
    var tint by state::tint
    val localTransform = state.localTransform

    //TODO: optimize
    fun baseAABB() = state.baseAABB ?: getAxisAlignedBoundingBox(model.mesh)
    private fun transformMatrix() = transform.toMatrix()


    private var despawned = false

    val renderInstance: RenderInstance = context.world.getOrBindModel(model).spawn(
        state.id, this, initialTransform = transformMatrix(), state.tint
    )


    init {
        state::tint.getBackingState().beforeChange {
            if (state.tint != it && !despawned) {
                renderInstance.setTintColor(it.color)
                renderInstance.setTintStrength(it.strength)
            }
        }.closeWithThis()
        onTransformChange {
            renderInstance.setTransform(it.toMatrix())
        }.closeWithThis()
    }

    fun joint(name: String): Transformable {
        val nodeId = model.nodeHierarchy.find { it.name == name }?.id ?: throw IllegalArgumentException(
            "No joint with name $name (actual: ${
                model.nodeHierarchy.toList().map { it.name }
            })"
        )
        return renderInstance.jointTransform(this, nodeId)
    }

    override fun cleanup() {
        despawned = true
        renderInstance.despawn()
    }

    override val boundingBox: AxisAlignedBoundingBox
        get() = baseAABB().transformed(transformMatrix())   //TODO: optimize

    override val transform: Transform
        get() = parentTransform.transform.mul(state.localTransform.transform)

    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        val localListener = state.localTransform.onTransformChange {
            callback(parentTransform.transform.mul(it))
        }
        val parentListener = parentTransform.onTransformChange {
            callback(it.mul(state.localTransform.transform))
        }
        return localListener.compose(parentListener).cast()
    }
}


class SimpleRenderObject(id: FunId, context: FunContext, model: Model) : FunOld(context, id) {
    val render = render(model)
}

class SimplePhysicsObject(id: FunId, context: FunContext, model: Model, physicsSystem: PhysicsSystem) : FunOld(context, id) {
    val physics = physics(physicsSystem)
    val render = render(model, physics)
}

