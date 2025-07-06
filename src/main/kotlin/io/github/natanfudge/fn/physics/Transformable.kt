package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.cast
import io.github.natanfudge.fn.util.compose
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

interface Transformable {
    val transform: Transform
    fun onTransformChange (callback: (Transform) -> Unit) : Listener<Transform>
}

//typealias Transformable = ListenableState<Transform>

val Transformable.translation get() = transform.translation
val Transformable.rotation get() = transform.rotation
val Transformable.scale get() = transform.scale


object RootTransformable : Transformable {
    override val transform: Transform = Transform()
    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        return Listener.Stub
    }
}


//interface Transformable {
//    object Root : Transformable {
//        override val translation: Vec3f = Vec3f.zero()
//        override val rotation: Quatf = Quatf.identity()
//        override val scale: Vec3f = Vec3f(1f, 1f, 1f)
//
//        override fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = Listener.Stub
//
//        override fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf> = Listener.Stub
//
//        override fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = Listener.Stub
//    }
//
//    val translation: Vec3f
//    val rotation: Quatf
//    val scale: Vec3f
//
//    fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f>
//    fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf>
//    fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f>
//}

//fun Transformable.calculateTransformMatrix(translation: Vec3f = this.translation, rotation: Quatf = this.rotation, scale: Vec3f = this.scale) =
//    Mat4f.translateRotateScale(
//        translation, rotation, scale
//    )



abstract class HierarchicalTransformable(val parentTransform: Transformable, parentFun: Fun, name: String) : Fun(parentFun, name), Transformable {
    val localTransform = FunTransform(this)

    override var transform: Transform = calculateTransform(parentTransform.transform, localTransform.transform)

    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        val localListener = localTransform.onTransformChange {
            transform = calculateTransform(parentTransform = parentTransform.transform, localTransform = it)
            callback(transform)
        }
        val parentListener = parentTransform.onTransformChange {
            transform = calculateTransform(parentTransform = it, localTransform = localTransform.transform)
        }
        return localListener.compose(parentListener).cast()
    }

//    final override var rotation = calculateRotation()
//        private set
//
//    final override var translation: Vec3f = calculateTranslation()
//        private set
//
//    final override var scale: Vec3f = calculateScale()
//        private set

    fun calculateTransform(parentTransform: Transform, localTransform: Transform) = parentTransform.mul(localTransform)


//
//    private fun calculateTranslation(
//        parentTranslation: Vec3f = parentTransform.translation, parentRotation: Quatf = parentTransform.rotation, parentScale: Vec3f = parentTransform.scale,
//        localTranslation: Vec3f = localTransform.translation,
//        //SUS: got this from chatgpt
//    ) = parentTranslation + (parentRotation.rotate(localTranslation * parentScale))
//
//    private fun calculateRotation(
//        parentRotation: Quatf = parentTransform.rotation, localRotation: Quatf = localTransform.rotation,
//    ): Quatf {
//        return parentRotation * localRotation
//    }
//
//    private fun calculateScale(
//        parentScale: Vec3f = parentTransform.scale, localScale: Vec3f = localTransform.scale,
//    ) = parentScale * localScale
//
//    private val translationStream = MutEventStream<Vec3f>()
//    private val rotationStream = MutEventStream<Quatf>()
//    private val scaleStream = MutEventStream<Vec3f>()




//
//
//    final override fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> {
//        return translationStream.listen(callback)
//    }
//
//    final override fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf> {
//        return rotationStream.listen(callback)
//    }
//
//    final override fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> {
//        return scaleStream.listen(callback)
//    }
//
//    init {
//        localTransform.onTranslationChanged {
//            translation = calculateTranslation(localTranslation = it)
//            translationStream.emit(translation)
//        }
//        parentTransform.onTranslationChanged {
//            translation = calculateTranslation(parentTranslation = it)
//            translationStream.emit(translation)
//        }.closeWithThis()
//
//        localTransform.onRotationChanged {
//            rotation = calculateRotation(localRotation = it)
//            rotationStream.emit(rotation)
//        }
//        parentTransform.onRotationChanged {
//            rotation = calculateRotation(parentRotation = it)
//            translation = calculateTranslation(parentRotation = it)
//            rotationStream.emit(rotation)
//            translationStream.emit(translation)
//        }.closeWithThis()
//
//        localTransform.onScaleChanged {
//            scale = calculateScale(localScale = it)
//            scaleStream.emit(scale)
//        }
//        parentTransform.onScaleChanged {
//            scale = calculateScale(parentScale = it)
//            translation = calculateTranslation(parentScale = it)
//            scaleStream.emit(scale)
//            translationStream.emit(translation)
//        }.closeWithThis()
//    }
}

//TODO: transforms are fucked

class FunTransform(parent: Fun) : Fun(parent, "transform"), Transformable {
    /**
     * Cached object to avoid allocating on every access to the transform
     */
    private var _transform: Transform = Transform()


    override var transform: Transform get() = _transform
        set(value) {
            _transform = value
            translation = transform.translation
            rotation = transform.rotation
            scale = transform.scale
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



     var translation by translationState
     var rotation by rotationState
     var scale by scaleState

//    override fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = translationState.beforeChange(callback)
//    override fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf> = rotationState.beforeChange(callback)
//    override fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = scaleState.beforeChange(callback)
}