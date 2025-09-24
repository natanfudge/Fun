package io.github.natanfudge.fn.physics

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.utils.find
import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.core.funValue
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Boundable
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.RenderInstance
import io.github.natanfudge.fn.render.Tint
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.render.getAxisAlignedBoundingBox
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.cast
import io.github.natanfudge.fn.util.compose
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

interface Transformable {
    val transform: Transform
    fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform>
}

//TODO: would like to have a ChildTransformable that accepts parent Transformable

/**
 * For now this is immutable, which is simpler and doesn't require making it a Fun I think
 */
class TransformNode(
    val localTransform: Transform,
    val parentTransform: Transformable,
) : Transformable {
    override var transform: Transform = parentTransform.transform.mul(localTransform)
    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        val parentListener = parentTransform.onTransformChange {
            transform = it.mul(localTransform)
            callback(transform)
        }
        return parentListener
    }
}


val Transformable.translation get() = transform.translation
val Transformable.rotation get() = transform.rotation
val Transformable.scale get() = transform.scale


object RootTransformable : Transformable {
    override val transform: Transform = Transform()
    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        return Listener.Stub
    }
}

class FunTransform(parent: Fun) : Fun(parent.id.child("transform"), parent), Transformable {
    override var transform: Transform
        get() = _transform
        set(value) {
            translation = value.translation
            rotation = value.rotation
            scale = value.scale
            _transform = value
        }

    val translationState = funValue<Vec3f>("translation", { Vec3f.zero() }){
        afterChange {
            _transform = _transform.copy(translation = it)
        }
    }
    var translation by translationState
    val rotationState  = funValue<Quatf>("rotation", { Quatf.identity() }){
        afterChange {
            _transform = _transform.copy(rotation = it)
        }
    }
    var rotation by rotationState
    val scaleState = funValue<Vec3f>("scale", { Vec3f(1f, 1f, 1f) }) {
        afterChange {
            _transform = _transform.copy(scale = it)
        }
    }
    var scale by scaleState

    /**
     * Cached object to avoid allocating on every access to the transform
     */
    private var _transform: Transform = Transform(translation, rotation, scale)

    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        val translationListener = translationState.beforeChange {
            callback(transform.copy(translation = it))
        }
        val rotationListener = rotationState.beforeChange {
            callback(transform.copy(rotation = it))
        }
        val scaleListener = scaleState.beforeChange {
            callback(transform.copy(scale = it))
        }
        return translationListener.compose(rotationListener).compose(scaleListener).cast()
    }
}

