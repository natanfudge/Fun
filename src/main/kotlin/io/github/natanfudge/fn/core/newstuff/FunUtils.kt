@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.chooseEditor
import io.github.natanfudge.fn.network.state.getFunSerializer
import io.github.natanfudge.fn.network.state.unsafeToNotNull
import io.github.natanfudge.fn.util.EventEmitter
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

//fun <T> NewFun.memo(key: String, dependencies: CacheDependencyKeys, ctr: () -> T): T {
//    val key = "$id[$key]"
//    return context.cache.requestInitialization(key, dependencies, ctr)
//}


class FunValueConfig<T> {
    var editor: ValueEditor<T>? = null
    @PublishedApi internal var beforeChange: ((value: T) -> Unit)? = null
    @PublishedApi internal var afterChange: ((value: T) -> Unit)? = null

    /**
     * The lifecycles of this callback is bound to the FunValue itself - when the FunValue dies the callback dies.
     */
    fun beforeChange(func: (value: T) -> Unit) {
        this.beforeChange = func
    }
    fun afterChange(func: (value: T) -> Unit) {
        this.afterChange = func
    }

    fun build(type: KType, serializer: KSerializer<T>, initialValue: T, ownerId: FunId, stateId: StateId): ClientFunValue<T> {
        val editor = (editor ?: chooseEditor(type.classifier as KClass<T & Any>)) as ValueEditor<T>
        val beforeChange = beforeChange
        val afterChange = afterChange

        // SLOW: too much code in inline function
        val funValue = ClientFunValue(
            initialValue,
            serializer, id = stateId, ownerId = ownerId, NewFunContextRegistry.getContext(), editor
        )
        if (beforeChange != null) {
            funValue.beforeChange(beforeChange)
        }
        if (afterChange != null) {
            funValue.afterChange(afterChange)
        }
        return funValue
    }
}

/**
 * When hot reload occurs, the state is not deleted, so when this function is called the state will be retained from before the reload.
 * Without hot reload, this just returns [initialValue]
 */
@PublishedApi
internal inline fun <reified T> NewFun.useOldStateIfPossible(initialValue: T, stateId: FunId): T {
    val parentId = this.id
    val oldState = context.stateManager.getState(parentId)?.getCurrentState()?.get(stateId)?.value
    return if (oldState is T) oldState else {
        if (oldState != null) println("Throwing out incompatible old state for $parentId:$stateId")
        initialValue
    }
}

inline fun <reified T> NewFun.funValue(
    initialValue: T?,
    stateId: FunId,
    config: FunValueConfig<T>.() -> Unit,
): ClientFunValue<T> {
    val config = FunValueConfig<T>().apply(config)
    return config.build(
        type = typeOf<T>(),
        serializer = getFunSerializer(),
        initialValue = useOldStateIfPossible(unsafeToNotNull(initialValue), stateId),
        ownerId = this.id,
        stateId = stateId
    )
}



internal fun checkListenersClosed(events: NewFunEvents)  = with(events){
    checkClosed(beforeFrame, frame, afterFrame, beforePhysics, physics, afterPhysics, input, guiError, appClosed)
}

private fun checkClosed(vararg events: EventEmitter<*>) {
    for (event in events) {
        check(!event.hasListeners)
    }
}