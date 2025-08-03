package io.github.natanfudge.fn.physics

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.render.Transform
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


val Transformable.translation get() = transform.translation
val Transformable.rotation get() = transform.rotation
val Transformable.scale get() = transform.scale


object RootTransformable : Transformable {
    override val transform: Transform = Transform()
    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        return Listener.Stub
    }
}

class FunTransform(parent: Fun) : Fun("transform", parent), Transformable {
    override var transform: Transform
        get() = _transform
        set(value) {
            translation = value.translation
            rotation = value.rotation
            scale = value.scale
            _transform = value
        }

    var translation by funValue<Vec3f>(Vec3f.zero()){
        afterChange {
            _transform = _transform.copy(translation = it)
        }
    }
    var rotation by funValue<Quatf>(Quatf.identity()){
        afterChange {
            _transform = _transform.copy(rotation = it)
        }
    }
    var scale by funValue<Vec3f>(Vec3f(1f, 1f, 1f)) {
        afterChange {
            _transform = _transform.copy(scale = it)
        }
    }

    /**
     * Cached object to avoid allocating on every access to the transform
     */
    private var _transform: Transform = Transform(translation, rotation, scale)

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

fun <T> KProperty0<T>.getBackingState(): ClientFunValue<T> {
    isAccessible = true
    val delegate = getDelegate()
    @Suppress("UNCHECKED_CAST")
    return delegate as ClientFunValue<T>
}