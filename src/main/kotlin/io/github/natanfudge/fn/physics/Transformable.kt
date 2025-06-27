package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.MutEventStream
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f


interface Transformable /*: IFun*/ {
    object Root/*(val parentFun: IFun)*/ : Transformable {
        override val translation: Vec3f = Vec3f.zero()
        override val rotation: Quatf = Quatf.identity()
        override val scale: Vec3f = Vec3f(1f,1f,1f)

        override fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = Listener.Stub

        override fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf> = Listener.Stub

        override fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f>  = Listener.Stub
    }
    companion object {

    }
    val translation: Vec3f
    val rotation: Quatf
    val scale: Vec3f

    fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f>
    fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf>
    fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f>
}

fun Transformable.calculateTransformMatrix(translation: Vec3f = this.translation, rotation: Quatf = this.rotation, scale: Vec3f = this.scale) =
    Mat4f.translateRotateScale(
        translation, rotation, scale
    )

abstract class HierarchicalTransformable(val parentTransform: Transformable, parentFun: Fun, name: String) : Fun(parentFun, name), Transformable {
    val localTransform = FunTransform(this)

    final override val translation: Vec3f
        get() = calculateTranslation()

    final override val rotation: Quatf
        get() = calculateRotation()

    final override val scale: Vec3f get() = calculateScale()

    private fun calculateTranslation(
        parentTranslation: Vec3f = parentTransform.translation, parentRotation: Quatf = parentTransform.rotation, parentScale: Vec3f = parentTransform.scale,
        localTranslation: Vec3f = localTransform.translation,
        //SUS: got this from chatgpt
    ) = parentTranslation + (parentRotation.rotate(localTranslation * parentScale))

    private fun calculateRotation(
        parentRotation: Quatf = parentTransform.rotation, localRotation: Quatf = localTransform.rotation,
    ) = parentRotation * localRotation

    //TODo: I should inline this somehow...
    private fun calculateScale(
        parentScale: Vec3f = parentTransform.scale, localScale: Vec3f = localTransform.scale,
    ) = parentScale * localScale

    private val translationStream = MutEventStream<Vec3f>()
    private val rotationStream = MutEventStream<Quatf>()
    private val scaleStream = MutEventStream<Vec3f>()


    final override fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> {
        return translationStream.listen(callback)
    }

    final override fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf> {
        return rotationStream.listen(callback)
    }

    final override fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> {
        return scaleStream.listen(callback)
    }

    init {
        localTransform.onTranslationChanged {
            translationStream.emit(calculateTranslation(localTranslation = it))
        }
        parentTransform.onTranslationChanged {
            translationStream.emit(calculateTranslation(parentTranslation = it))
        }.closeWithThis()

        localTransform.onRotationChanged {
            rotationStream.emit(calculateRotation(localRotation = it))
        }
        parentTransform.onRotationChanged {
            rotationStream.emit(calculateRotation(parentRotation = it))
            translationStream.emit(calculateTranslation(parentRotation = it))
        }.closeWithThis()

        localTransform.onScaleChanged {
            scaleStream.emit(calculateScale(localScale = it))
        }
        parentTransform.onScaleChanged {
            scaleStream.emit(calculateScale(parentScale = it))
            translationStream.emit(calculateTranslation(parentScale = it))
        }.closeWithThis()
    }
}

class FunTransform(parent: Fun) : Fun(parent, "transform"), Transformable {
    val translationState = funValue<Vec3f>(Vec3f.zero(), "translation")
    val rotationState = funValue<Quatf>(Quatf.identity(), "rotation")
    val scaleState = funValue<Vec3f>(Vec3f(1f,1f,1f), "scaling")

    override var translation by translationState
    override var rotation by rotationState
    override var scale by scaleState

    override fun onTranslationChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = translationState.beforeChange(callback)
    override fun onRotationChanged(callback: (Quatf) -> Unit): Listener<Quatf> = rotationState.beforeChange(callback)
    override fun onScaleChanged(callback: (Vec3f) -> Unit): Listener<Vec3f> = scaleState.beforeChange(callback)
}