package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.core.funValue
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.cast
import io.github.natanfudge.fn.util.compose
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

//TODO: the issue is bad Transform.mul, need to think about replacing this with a Matrix and storing a seperate Transform where applicable.
// But note we do onTransformChange {it.translation}, so seems like we do depend on knowing the decomposed multiplied values.
// So our options are probably either fix mul and hope it's correct and fast enough, or try multiplying real Mat4f and then decomposing the matrix when we need.
// Seems like currently we only access translation so decomposition would be easy for that - just get the last column.
// If the dev wants to access render-wise rotation or scaling, that seems pretty uncommon so we could have a slow operator for that.
// If he wants position or rotation he usually just means the physical position or rotation so he could access those values from there and it would be more correct.
// Generally beforeTransformChange should be a lower level construct that passes along a matrix. If we want to listen to physics changes
interface Transformable {
    val transform: Transform
    fun beforeTransformChange(callback: (Transform) -> Unit): Listener<Transform>
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
    override fun beforeTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        val parentListener = parentTransform.beforeTransformChange {
            transform = it.mul(localTransform)
            callback(transform)
        }
        return parentListener
    }

    override fun toString(): String {
        return "Locally[$localTransform] Globally[$transform]"
    }
}


val Transformable.translation get() = transform.translation
val Transformable.rotation get() = transform.rotation
val Transformable.scale get() = transform.scale


object RootTransformable : Transformable {
    override val transform: Transform = Transform()
    override fun beforeTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
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

    override fun toString(): String {
        return "Fun$_transform"
    }

    override fun beforeTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
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

