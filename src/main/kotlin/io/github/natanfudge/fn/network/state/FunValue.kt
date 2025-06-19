@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.network.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.funedit.*
import io.github.natanfudge.fn.network.*
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Tint
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlinx.serialization.KSerializer
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
inline fun <reified T> Fun.funValue(
    value: T,
    id: FunId,
//    owner: Fun,
    editor: ValueEditor<T> = chooseEditor(typeOf<T>().classifier as KClass<T & Any>),
//    autocloseSetValue: Boolean = true,
//    /**
//     * If not null, a listener will be automatically registered for [onSetValue].
//     * If [autocloseSetValue] is true, The listener will be automatically closed when this [Fun] is closed.
//     */
//    noinline onSetValue: ((value: T) -> Unit)? = null,
): FunValue<T> {
    val value = FunValue(value, getSerializerExtended<T>(), id, this, editor)
//    if (onSetValue != null) {
//        val listener = value.change.listen(onSetValue)
//        if (autocloseSetValue) closeEvent.listen { listener.close() }
//    }
    return value
}


@PublishedApi
internal fun <T> chooseEditor(kClass: KClass<T & Any>): ValueEditor<T> = when (kClass) {
    AxisAlignedBoundingBox::class -> AABBEditor
    Vec3f::class -> Vec3fEditor
    Quatf::class -> QuatfEditor
    Color::class -> ColorEditor
    Tint::class -> TintEditor
    Boolean::class -> BooleanEditor
    Float::class -> FloatEditor
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
class FunValue<T>(
    value: T, private val serializer: KSerializer<T>, private val id: FunId, private val owner: Fun,
    override val editor: ValueEditor<T>,
) : ReadWriteProperty<Fun, T>, FunState<T> {

    private var composeValue by mutableStateOf(value)
//    private var registered: Boolean = false

    private val _change = EventStream.create<T>()

    /**
     * Changes are emitted BEFORE the field changes, so you can use both the old value by accessing the field, and the new value by using the
     * passed value in listen {}.
     */
    val change: EventStream<T> = _change
//        field = EventStream.create<T>()

    init {
        owner.context.stateManager.registerState(
            holderKey = owner.id,
            propertyKey = id,
            state = this as FunValue<Any?>
        )
    }

    override var value: T
        get() = composeValue
        set(value) {
            owner.context.sendStateChange(
                StateKey(owner.id, id),
                StateChangeValue.SetProperty(value.toNetwork(serializer)),
            )

            _change.emit(value)
            composeValue = value
        }

//    private var thisRef: Fun? = null
//    private var property: KProperty<*>? = null


    override fun toString(): String {
        return value.toString()
    }

    @InternalFunApi
    override fun applyChange(change: StateChangeValue) {
        require(change is StateChangeValue.SetProperty)
        this.composeValue = change.value.decode(serializer)
    }

//    private fun capturePropertyValues(thisRef: Fun, property: KProperty<*>) {
//        if (!registered) {
//            this.thisRef = thisRef
//            this.property = property
//            registered = true
//
//
//            // It's possible some new data came through before we managed to captured the values, so we apply it now.
//            thisRef.context.stateManager.setToPendingValue(
//                holderKey = thisRef.id,
//                propertyKey = property.name,
//                state = this as FunValue<Any?>
//            )
//        }
//    }

    /**
     * Gets the current value of the property.
     *
     * On first access, the property is registered with the client and any pending
     * updates are applied.
     */
    override fun getValue(thisRef: Fun, property: KProperty<*>): T {
//        capturePropertyValues(thisRef, property)
        return composeValue
    }

    /**
     * Sets a new value for the property and synchronizes it with all other clients.
     *
     * On first access, the property is registered with the client.
     */
    override fun setValue(thisRef: Fun, property: KProperty<*>, value: T) {
        this.value = value
//        capturePropertyValues(thisRef, property)
//        _setValue(value)
    }

//    private fun _setValue(value: T) {
////        val thisRef = thisRef
//            // Important to do this first so that if it throws then it won't update the value
//
//        this._value = value
//    }

}
