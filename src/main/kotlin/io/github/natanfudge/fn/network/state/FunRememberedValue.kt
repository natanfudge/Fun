package io.github.natanfudge.fn.network.state

import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.util.Listener
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Value that can be stored persistently but shouldn't be interactable from the outside.
 */
class FunRememberedValue<T>(initialValue: T): FunState<T>, ReadWriteProperty<Any?,T> {
    override fun applyChange(change: StateChangeValue) {

    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    override var value: T = initialValue

    override fun beforeChange(callback: (T) -> Unit): Listener<T> {
        return Listener.Stub
    }

    override val editor: ValueEditor<T>
        get() = ValueEditor.Missing as ValueEditor<T>
}