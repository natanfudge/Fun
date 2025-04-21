package io.github.natanfudge.fn.network.state

import io.github.natanfudge.fn.network.Fun
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Creates a property delegate that automatically synchronizes its value across all clients.
 *
 * This function is used to create properties in [Fun] components that will be automatically
 * synchronized when their value changes.
 *
 * @sample io.github.natanfudge.fn.test.example.network.state.StateFunValueExamples.funValueExample
 * @see Fun
 */
inline fun <reified T> funValue(value: T): FunValue<T> = FunValue(value, serializer())

/**
 * A property delegate that synchronizes its value across all clients in a multiplayer environment.
 *
 * When a FunState property is modified, the change is automatically sent to all other clients.
 * Similarly, when another client modifies the property, the change is automatically applied locally.
 *
 * @see funValue
 * @see Fun
 */
class FunValue<T>(private var value: T, private val serializer: KSerializer<T>) : KoinComponent,
    ReadWriteProperty<Fun, T>, FunState {
    private var registered: Boolean = false


    override fun toString(): String {
        return value.toString()
    }

    @InternalFunApi
    override fun applyChange(change: StateChange) {
        require(change is StateChange.SetProperty)
        this.value = change.value.decode(serializer)
    }

    /**
     * Gets the current value of the property.
     *
     * On first access, the property is registered with the client and any pending
     * updates are applied.
     */
    override fun getValue(thisRef: Fun, property: KProperty<*>): T {
        if (!registered) {
            registered = true
            thisRef.client.registerState(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunValue<Any?>
            )
            thisRef.client.setPendingValue(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunValue<Any?>
            )
        }

        return value
    }

    /**
     * Sets a new value for the property and synchronizes it with all other clients.
     *
     * On first access, the property is registered with the client.
     */
    override fun setValue(thisRef: Fun, property: KProperty<*>, value: T) {
        if (!registered) {
            registered = true
            thisRef.client.registerState(
                holderKey = thisRef.id,
                propertyKey = property.name,
                state = this as FunValue<Any?>
            )
        }

        this.value = value

        thisRef.client.sendUpdate(
            holderKey = thisRef.id,
            propertyKey = property.name,
            change = StateChange.SetProperty(value.toNetwork(serializer)),
        )
    }
}
