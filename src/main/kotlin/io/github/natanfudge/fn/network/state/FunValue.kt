@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.network.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.funedit.AABBEditor
import io.github.natanfudge.fn.compose.funedit.ColorEditor
import io.github.natanfudge.fn.compose.funedit.QuatfEditor
import io.github.natanfudge.fn.compose.funedit.TintEditor
import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.compose.funedit.Vec3fEditor
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.StateKey
import io.github.natanfudge.fn.network.getSerializerExtended
import io.github.natanfudge.fn.network.sendStateChange
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Tint
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.typeOf


/**
 * Creates a property delegate that automatically synchronizes its value across all clients.
 *
 * This function is used to create properties in [Fun] components that will be automatically
 * synchronized when their value changes.
 *
 * @sample io.github.natanfudge.fn.test.example.network.state.StateFunValueExamples.funValueExample
 * @see Fun
 */
inline fun <reified T> funValue(
    value: T,
    editor: ValueEditor<T> = chooseEditor(typeOf<T>().classifier as KClass<T & Any>),
    noinline onSetValue: (old: T, new: T) -> Unit = {o, n ->},
): FunValue<T> = FunValue(value, getSerializerExtended<T>(), onSetValue, editor)



@PublishedApi
internal fun <T> chooseEditor(kClass: KClass<T & Any>): ValueEditor<T> = when (kClass) {
    AxisAlignedBoundingBox::class -> AABBEditor
    Vec3f::class -> Vec3fEditor
    Quatf::class -> QuatfEditor
    Color::class -> ColorEditor
    Tint::class -> TintEditor
    else -> ValueEditor.Missing
} as ValueEditor<T>

/**
 * A property delegate that synchronizes its value across all clients in a multiplayer environment.
 *
 * When a FunState property is modified, the change is automatically sent to all other clients.
 * Similarly, when another client modifies the property, the change is automatically applied locally.
 *
 * @see funValue
 * @see Fun
 */
class FunValue<T>(value: T, private val serializer: KSerializer<T>, private val onSetValue: (old: T, new: T) -> Unit, editor: ValueEditor<T>) : KoinComponent,
    ReadWriteProperty<Fun, T>, FunState {

    private var _value by mutableStateOf(value)
    private var registered: Boolean = false

    override val editor: ValueEditor<Any?> = editor as ValueEditor<Any?>
    override var value: Any?
        get() = _value
        set(value) {
            _setValue(value as T)
        }

    private var thisRef: Fun? = null
    private var property: KProperty<*>? = null


    override fun toString(): String {
        return value.toString()
    }

    @InternalFunApi
    override fun applyChange(change: StateChangeValue) {
        require(change is StateChangeValue.SetProperty)
        this._value = change.value.decode(serializer)
    }

    private fun capturePropertyValues(thisRef: Fun, property: KProperty<*>) {
        if (!registered) {
            this.thisRef = thisRef
            this.property = property
            registered = true
            thisRef.context.stateManager.registerState(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunValue<Any?>
            )

            // It's possible some new data came through before we managed to captured the values, so we apply it now.
            thisRef.context.stateManager.setToPendingValue(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunValue<Any?>
            )
        }
    }

    /**
     * Gets the current value of the property.
     *
     * On first access, the property is registered with the client and any pending
     * updates are applied.
     */
    override fun getValue(thisRef: Fun, property: KProperty<*>): T {
        capturePropertyValues(thisRef, property)
        return _value
    }

    /**
     * Sets a new value for the property and synchronizes it with all other clients.
     *
     * On first access, the property is registered with the client.
     */
    override fun setValue(thisRef: Fun, property: KProperty<*>, value: T) {
        capturePropertyValues(thisRef, property)
        _setValue(value)
    }

    private fun _setValue(value: T) {
        val thisRef = thisRef
        val property = property
        if (thisRef != null && property != null) {
            // Important to do this first so that if it throws then it won't update the value
            thisRef.context.sendStateChange(
                StateKey(thisRef.id, property.name),
                StateChangeValue.SetProperty(value.toNetwork(serializer)),
            )
        }

        val old = this._value
        this._value = value
        onSetValue(old, value)
    }

}
