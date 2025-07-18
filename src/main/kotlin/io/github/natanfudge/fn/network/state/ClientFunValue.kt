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
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.typeOf

/**
 * When hot reload occurs, the state is not deleted, so when this function is called the state will be retained from before the reload.
 * Without hot reload, this just returns [initialValue]
 */
@PublishedApi
internal inline fun <reified T> Fun.useOldStateIfPossible(initialValue: T, stateId: FunId): T {
    val parentId = this.id
    val oldState = context.stateManager.getState(parentId)?.getCurrentState()?.get(stateId)?.value
    return if (oldState is T) oldState else {
        if (oldState != null) println("Throwing out incompatible old state for $parentId:$stateId")
        initialValue
    }
}

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
    initialValue: T?,
    id: FunId,
    editor: ValueEditor<T> = chooseEditor(typeOf<T>().classifier as KClass<T & Any>),
    /**
     * If not null, a listener will be automatically registered for [beforeChange].
     */
    noinline beforeChange: ((value: T) -> Unit)? = null,
    noinline afterChange: ((value: T) -> Unit)? = null,
): ClientFunValue<T> {
    // SLOW: too much code in inline function
    val funValue = ClientFunValue(
        useOldStateIfPossible(unsafeToNotNull(initialValue), id),
        getFunSerializer<T>(), id, this.id, this.context, editor
    )
    if (beforeChange != null) {
        funValue.beforeChange(beforeChange)
    }
    if (afterChange != null) {
        funValue.afterChange(afterChange)
    }
    return funValue
}

inline fun <reified T> Fun.funValue(
    initialValue: T?,
    editor: ValueEditor<T> = chooseEditor(typeOf<T>().classifier as KClass<T & Any>),
    /**
     * If not null, a listener will be automatically registered for [beforeChange].
     */
    noinline beforeChange: ((value: T) -> Unit)? = null,
    noinline afterChange: ((value: T) -> Unit)? = null,
): PropertyDelegateProvider<Any, ClientFunValue<T>> = PropertyDelegateProvider { _, property ->
    funValue(initialValue, property.name, editor, beforeChange, afterChange)
}


@PublishedApi
internal fun <T> unsafeToNotNull(value: T?): T = value as T


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
    value: T, private val serializer: KSerializer<T>, private val id: StateId,
    private val ownerId: FunId,
    private val context: FunStateContext,
    override val editor: ValueEditor<T>,
) : ReadWriteProperty<Any?, T>, FunState<T> {

//    private var composeValue
//    private var registered: Boolean = false

    private val beforeChange = EventStream.create<T>()
    private val afterChange = EventStream.create<T>()


    init {
        context.stateManager.registerState(
            holderKey = ownerId,
            propertyKey = id,
            state = this as ClientFunValue<Any?>
        )
    }

    /**
     * Changes are emitted BEFORE setting a new value, and are passed the new value.
     */
    override fun beforeChange(callback: (T) -> Unit): Listener<T> = beforeChange.listenUnscoped(callback)
    fun afterChange(callback: (T) -> Unit): Listener<T> = afterChange.listenUnscoped(callback)

//    fun afterChange(callback: (T) -> Unit):

    override var value: T = value
        set(value) {
            // do this only in a ServerFunValue
//            owner.context.sendStateChange(
//                StateKey(owner.id, id),
//                StateChangeValue.SetProperty(value.toNetwork(serializer)),
//            )
            beforeChange.emit(value)
            field = value
            afterChange.emit(value)
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
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
//        capturePropertyValues(thisRef, property)
        return value
    }

    /**
     * Sets a new value for the property and synchronizes it with all other clients.
     *
     * On first access, the property is registered with the client.
     */
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
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
