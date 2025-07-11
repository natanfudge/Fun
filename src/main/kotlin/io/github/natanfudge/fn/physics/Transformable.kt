package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.core.FunOld
import io.github.natanfudge.fn.network.state.FunState
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.cast
import io.github.natanfudge.fn.util.compose
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlinx.serialization.Transient
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

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


class FunTransformOld(parent: FunOld) : FunOld(parent, "transform"), Transformable {
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
}


class FunTransform(override val id: FunId) : Fun(), Transformable {
    override var transform: Transform
        get() = _transform
        set(value) {
            _transform = value
            translation = value.translation
            rotation = value.rotation
            scale = value.scale
        }


    var translation by funValue<Vec3f>(Vec3f.zero(), "translation") {
        _transform = _transform.copy(translation = it)
    }
    var rotation by funValue<Quatf>(Quatf.identity(), "rotation") {
        _transform = _transform.copy(rotation = it)
    }
    var scale by funValue<Vec3f>(Vec3f(1f, 1f, 1f), "scaling") {
        _transform = _transform.copy(scale = it)
    }

    /**
     * Cached object to avoid allocating on every access to the transform
     */
    @Transient
    private var _transform: Transform = Transform(this@FunTransform.translation, this@FunTransform.rotation, this@FunTransform.scale)

    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        val translationListener = ::translation.getBackingState().beforeChange {
            callback(transform.copy(translation = it))
        }
        val rotationListener = ::rotation.getBackingState().beforeChange {
            callback(transform.copy(rotation = it))
        }
        val scaleListener = ::scale.getBackingState().beforeChange {
            callback(transform.copy(scale = it))
        }
        return translationListener.compose(rotationListener).compose(scaleListener).cast()
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> KProperty0<T>.getBackingState(): FunState<T> {
    isAccessible = true
    return getDelegate() as? FunState<T> ?: error("No backing state found for $this")
}
