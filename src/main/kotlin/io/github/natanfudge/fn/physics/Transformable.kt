package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.cast
import io.github.natanfudge.fn.util.compose
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

interface Transformable {
    val transform: Transform
    fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform>
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



//abstract class HierarchicalTransformable(val parentTransform: Transformable, parentFun: Fun, name: String) : Fun(parentFun, name), Transformable {
//    val localTransform = FunTransform(this)
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
//}


class FunTransform(parent: Fun) : Fun(parent, "transform"), Transformable {



    override var transform: Transform
        get() = _transform
        set(value) {
            _transform = value
            translationState.value = value.translation
            rotationState.value = value.rotation
            scaleState.value = value.scale
        }


    val translationState = funValue<Vec3f>(Vec3f.zero(), "translation") {
        _transform = _transform.copy(translation = it)
    }
    val rotationState = funValue<Quatf>(Quatf.identity(), "rotation") {
        _transform = _transform.copy(rotation = it)
    }
    val scaleState = funValue<Vec3f>(Vec3f(1f, 1f, 1f), "scaling") {
        _transform = _transform.copy(scale = it)
    }

    var translation by translationState
    var rotation by rotationState
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



//    override fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = translationState.beforeChange(callback)
//    override fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf> = rotationState.beforeChange(callback)
//    override fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = scaleState.beforeChange(callback)
}
