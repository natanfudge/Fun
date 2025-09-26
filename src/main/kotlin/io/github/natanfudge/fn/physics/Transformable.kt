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

//TODO: the issue is bad Transform.mul, need to think about replacing this with a Matrix and storing a seperate Transform where applicable.
// But note we do onTransformChange {it.translation}, so seems like we do depend on knowing the decomposed multiplied values.
// So our options are probably either fix mul and hope it's correct and fast enough, or try multiplying real Mat4f and then decomposing the matrix when we need.
// Seems like currently we only access translation so decomposition would be easy for that - just get the last column.
// If the dev wants to access render-wise rotation or scaling, that seems pretty uncommon so we could have a slow operator for that.
// If he wants position or rotation he usually just means the physical position or rotation so he could access those values from there and it would be more correct.
// Generally beforeTransformChange should be a lower level construct that passes along a matrix. If we want to listen to physics changes

interface Transformable {
    val transform: Mat4f
    fun beforeTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f>
}

//TODO: would like to have a ChildTransformable that accepts parent Transformable

/**
 * For now this is immutable, which is simpler and doesn't require making it a Fun I think
 */
class TransformNode(
    val localTransform: Transform,
    val parentTransform: Transformable,
) : Transformable {
    private val localMatrix = localTransform.toMatrix()
    override var transform: Mat4f = parentTransform.transform.mul(localMatrix)
    override fun beforeTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f> {
        val parentListener = parentTransform.beforeTransformChange {
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
    override fun beforeTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f> {
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
        afterChange {
            transform = withTransform(translate = it)
        }
    }
    var translation by translationState
    val rotationState = funValue<Quatf>("rotation", { Quatf.identity() }) {
        afterChange {
            transform = withTransform(rotate = it)
        }
    }
    var rotation by rotationState
    val scaleState = funValue<Vec3f>("scale", { Vec3f(1f, 1f, 1f) }) {
        afterChange {
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

    override fun beforeTransformChange(callback: (Mat4f) -> Unit): Listener<Mat4f> {
        val translationListener = translationState.beforeChange {
            //TODo: slow, would like to reuse the new transform we have in afterChange of the translation/rotate/scaleState,
            // make it be beforeChange there and then afterChange here. But I remember making it be beforeChange can cause bugs so need to test that first.
            callback(withTransform(translate = it))
        }
        val rotationListener = rotationState.beforeChange {
            callback(withTransform(rotate = it))
        }
        val scaleListener = scaleState.beforeChange {
            callback(withTransform(scale = it))
        }
        return translationListener.compose(rotationListener).compose(scaleListener).cast()
    }
}

