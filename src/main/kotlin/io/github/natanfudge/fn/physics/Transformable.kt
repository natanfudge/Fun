package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.core.funValue
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.cast
import io.github.natanfudge.fn.util.compose
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f



interface Transformable {
    val transform: Mat4f
    fun afterTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f>
}


/**
 * For now this is immutable, which is simpler and doesn't require making it a Fun I think
 */
class TransformNode(
    val localTransform: Transform,
    val parentTransform: Transformable,
) : Transformable {
    private val localMatrix = localTransform.toMatrix()
    override var transform: Mat4f = parentTransform.transform.mul(localMatrix)
    override fun afterTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f> {
        val parentListener = parentTransform.afterTransformChange {
            transform = it.mul(localMatrix)
            callback(transform)
        }
        return parentListener
    }

    override fun toString(): String {
        return "Locally[$localTransform] Globally[$transform]"
    }
}


//val Transformable.translation get() = transform.translation
//val Transformable.rotation get() = transform.rotation
//val Transformable.scale get() = transform.scale


object RootTransformable : Transformable {
    override val transform: Mat4f = Mat4f.identity()
    override fun afterTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f> {
        return Listener.Stub
    }
}

class FunTransform(parent: Fun) : Fun(parent.id.child("transform"), parent), Transformable {
//    override val transform: Mat4f
//        get() = _transform
//        set(value) {
//            translation = value.translation
//            rotation = value.rotation
//            scale = value.scale
//            _transform = value
//        }

    private fun withTransform(translate: Vec3f = translationState.value, rotate: Quatf = rotationState.value, scale: Vec3f = scaleState.value) =
        Mat4f.translateRotateScale(translate, rotate, scale)

    val translationState = funValue<Vec3f>("translation", { Vec3f.zero() }) {
        beforeChange {
            transform = withTransform(translate = it)
        }
    }
    var translation by translationState
    val rotationState = funValue<Quatf>("rotation", { Quatf.identity() }) {
        beforeChange {
            transform = withTransform(rotate = it)
        }
    }
    var rotation by rotationState
    val scaleState = funValue<Vec3f>("scale", { Vec3f(1f, 1f, 1f) }) {
        beforeChange {
            transform = withTransform(scale = it)
        }
    }
    var scale by scaleState

    /**
     * Cached object to avoid allocating on every access to the transform
     */
    override var transform: Mat4f = Mat4f.translateRotateScale(translation, rotation, scale)
        private set

    fun set(transform: Transform) {
        translation = transform.translation
        rotation = transform.rotation
        scale = transform.scale
    }

    override fun toString(): String {
        return "FunTransform[translate=$translation, rotate=$rotation, scale=$scale]"
    }

//     fun beforeTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f> {
//        val translationListener = translationState.afterChange {
//            callback(transform)
//        }
//        val rotationListener = rotationState.afterChange {
//            callback(transform)
//        }
//        val scaleListener = scaleState.afterChange {
//            callback(transform)
//        }
//        return translationListener.compose(rotationListener).compose(scaleListener).cast()
//    }

    override fun afterTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f> {
        val translationListener = translationState.afterChange {
            callback(transform)
        }
        val rotationListener = rotationState.afterChange {
            callback(transform)
        }
        val scaleListener = scaleState.afterChange {
            callback(transform)
        }
        return translationListener.compose(rotationListener).compose(scaleListener).cast()
    }
}

