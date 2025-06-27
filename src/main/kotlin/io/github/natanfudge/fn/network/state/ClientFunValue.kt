@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.network.state

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.funedit.*
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.getSerializerExtended
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Tint
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.fn.util.Listener
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
    noinline onSetValue: ((value: T) -> Unit)? = null,
): ClientFunValue<T> {
    val value = ClientFunValue(value, getFunSerializer<T>(), id, this, editor)
    if (onSetValue != null) {
        value.onChange(onSetValue)
//        if (autocloseSetValue) closeEvent.listen { listener.close() }
    }
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
    Int::class -> IntEditor
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
class ClientFunValue<T>(
    value: T, private val serializer: KSerializer<T>, private val id: FunId, private val owner: Fun,
    override val editor: ValueEditor<T>,
) : ReadWriteProperty<Fun, T>, FunState<T> {

//    private var composeValue
//    private var registered: Boolean = false

    private val change = EventStream.create<T>()


    init {
        owner.context.stateManager.registerState(
            holderKey = owner.id,
            propertyKey = id,
            state = this as ClientFunValue<Any?>
        )
    }

    override fun onChange(callback: (T) -> Unit): Listener<T> = change.listen(callback)

    override var value: T = value
        set(value) {
            // do this only in a ServerFunValue
//            owner.context.sendStateChange(
//                StateKey(owner.id, id),
//                StateChangeValue.SetProperty(value.toNetwork(serializer)),
//            )
            change.emit(value)
            field = value
//            composeValue = value
        }

//    private var thisRef: Fun? = null
//    private var property: KProperty<*>? = null


    override fun toString(): String {
        return value.toString()
    }

    @InternalFunApi
    override fun applyChange(change: StateChangeValue) {
        require(change is StateChangeValue.SetProperty)
        this.value = change.value.decode(serializer)
    }

    /**
     * Gets the current value of the property.
     *
     * On first access, the property is registered with the client and any pending
     * updates are applied.
     */
    override fun getValue(thisRef: Fun, property: KProperty<*>): T {
//        capturePropertyValues(thisRef, property)
        return value
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
