@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.network.state

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.funedit.*
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.core.FunStateContext
import io.github.natanfudge.fn.core.StateId
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
    else if Enum::class.java.isAssignableFrom(kClass.java) -> EnumEditor(kClass.java.enumConstants.toList() as List<Enum<*>>)
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
    value: T, private val serializer: KSerializer<T>, private val id: StateId,
    private val ownerId: FunId,
    private val context: FunStateContext,
    override val editor: ValueEditor<T>,
) : ReadWriteProperty<Any?, T>, FunState<T> {

    private val beforeChange = EventStream.create<T>("beforeChange")
    private val afterChange = EventStream.create<T>("afterChange")


    init {
        context.stateManager.registerState(
            holderKey = ownerId,
            propertyKey = id,
            state = this as ClientFunValue<Any?>
        )
    }

    private val listenerName = "$ownerId#$id"

    /**
     * Changes are emitted BEFORE setting a new value, and are passed the new value.
     */
    override fun beforeChange(callback: (T) -> Unit): Listener<T> = beforeChange.listenUnscoped(listenerName, callback)
    fun afterChange(callback: (T) -> Unit): Listener<T> = afterChange.listenUnscoped(listenerName, callback)
    override var value: T = value
        set(value) {
            beforeChange.emit(value)
            field = value
            afterChange.emit(value)
        }



    override fun toString(): String {
        return value.toString()
    }

    @InternalFunApi
    override fun applyChange(change: StateChangeValue) {
        error(" TO DO")
//        require(change is StateChangeValue.SetProperty<*>)
//        this.value = change.value.decode(serializer)
    }

    /**
     * Gets the current value of the property.
     *
     * On first access, the property is registered with the client and any pending
     * updates are applied.
     */
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    /**
     * Sets a new value for the property and synchronizes it with all other clients.
     *
     * On first access, the property is registered with the client.
     */
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}
