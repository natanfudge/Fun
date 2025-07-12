package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.chooseEditor
import io.github.natanfudge.fn.network.state.getFunSerializer
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.typeOf


@Serializable
abstract class Fun() {
    abstract val id: FunId

    // TODO: need to think how to properly scope this, a ThreadLocal sounds cool (for FunContextRegistry.getContext()).
    val context: FunStateContext = FunContextRegistry.getContext()

    private var registered = false

    @PublishedApi
    internal fun registerIfNeeded() {
        if (!registered) {
            // OLD COMMENT:
            // We're gonna allow reregistering the fun state in case we hot reloaded, in order to reuse the state.
            // Before hot reload, there is no excuse to register the same Fun state twice.
            // After hot reload, the line gets blurry and there's no way to know whether a state is "before hot reload state" or "previous app state"
            // In the future we might seperate those, but this is good enough for now.


            // NEW COMMENT:
            // Just allow reregister for now to simplify
            context.stateManager.register(id, allowReregister = true)
            registered = true
        }
    }

//    inline fun <reified T> fromConstructor(
//        id: StateId,
//        editor: ValueEditor<T> = chooseEditor(typeOf<T>().classifier as KClass<T & Any>),
//        /**
//         * If not null, a listener will be automatically registered for [onSetValue].
//         */
//        noinline onSetValue: ((value: T) -> Unit)? = null,
//    ) = funValue(unsafeNull(), id, editor, onSetValue)

    @Suppress("UNCHECKED_CAST")
    fun <T> lateInit(): T = null as T

    inline fun <reified T> funValue(
        initialValue: T,
        id: StateId,
        editor: ValueEditor<T> = chooseEditor(typeOf<T>().classifier as KClass<T & Any>),
        /**
         * If not null, a listener will be automatically registered for [onSetValue].
         */
        noinline onSetValue: ((value: T) -> Unit)? = null,
    ): ClientFunValue<T> {
        registerIfNeeded()
        val funValue = ClientFunValue(
            useOldStateIfPossible(initialValue, parentId = this.id, stateId = id),
            getFunSerializer<T>(), id, this.id, context, editor
        )
        if (onSetValue != null) {
            funValue.beforeChange(onSetValue)
        }
        return funValue
    }


    fun child(childId: FunId) = id.child(childId)

    /**
     * When hot reload occurs, the state is not deleted, so when this function is called the state will be retained from before the reload.
     * Without hot reload, this just returns [initialValue]
     */
    @PublishedApi
    internal inline fun <reified T> useOldStateIfPossible(initialValue: T, parentId: FunId, stateId: StateId): T {
        val oldState = context.stateManager.getState(parentId)?.getCurrentState()?.get(stateId)?.value
        return if (oldState is T) oldState else initialValue
    }
}

//// TODO: in the future, we need to think about a mechanism where the server decide on the one true ID and gives it to the client.
//fun uniqueId(prefix: String = "") = prefix + UUID.randomUUID()