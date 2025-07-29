@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.mte.World
import io.github.natanfudge.fn.network.state.*
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

    @PublishedApi
    internal var beforeChange: ((value: T) -> Unit)? = null

    @PublishedApi
    internal var afterChange: ((value: T) -> Unit)? = null

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
 * Checks if a value is of a given type. Useful to pass around when it's not possible to use a reified type to check if something is of generic type T.
 */
typealias TypeChecker = (Any?) -> Boolean

/**
 * When hot reload occurs, the state is not deleted, so when this function is called the state will be retained from before the reload.
 * Without hot reload, this just returns [initialValue]
 */
@PublishedApi
internal
fun <T> NewFun.useOldStateIfPossible(initialValue: () -> T, stateId: FunId, typeChecker: TypeChecker): T {
    val parentId = this.id
    val oldState = context.stateManager.getState(parentId)?.getCurrentState()?.get(stateId)?.value
    return if (typeChecker(oldState)) oldState as T else {
        if (oldState != null) println("Throwing out incompatible old state for $parentId:$stateId")
        initialValue()
    }
}

inline fun <reified T> NewFun.useOldStateIfPossible(noinline initialValue: () -> T, stateId: FunId): T = useOldStateIfPossible(initialValue, stateId) {
    it is T
}

inline fun <reified T> NewFun.funValue(
    /**
     * It's possible to specify a null initial value even when the expected type is not nullable.
     * This signifies that [initialValue] is not expected to be called, and that we rely on the stored value to be used.
     */
    noinline initialValue: () -> T?,
    stateId: StateId,
    config: FunValueConfig<T>.() -> Unit,
): ClientFunValue<T> {
    val config = FunValueConfig<T>().apply(config)
    return config.build(
        type = typeOf<T>(),
        serializer = getFunSerializer(),
        initialValue = useOldStateIfPossible(initialValue as () -> T, stateId),
        ownerId = this.id,
        stateId = stateId
    )
}


fun <T> NewFun.funSet(stateId: StateId, serializer: KSerializer<T>, items: () -> MutableSet<T>, editor: ValueEditor<Set<T>>): FunSet<T> {
    val set = FunSet(useOldStateIfPossible(items, stateId), stateId, this.id, serializer, editor)
    context.stateManager.registerState(id, stateId, set)
    return set
}

fun <T> NewFun.memo(stateId: StateId, typeChecker: TypeChecker, initialValue: () -> T?): FunRememberedValue<T> {
    val oldState = useOldStateIfPossible(initialValue as () -> T, stateId, typeChecker)
    val rememberedValue = FunRememberedValue(oldState)
    context.stateManager.registerState(id, stateId, rememberedValue)
    return rememberedValue
}


internal fun checkListenersClosed(events: NewFunEvents) = with(events) {
    checkClosed(beforeFrame, frame, afterFrame, beforePhysics, physics, afterPhysics, input, guiError, appClosed)
}

private fun checkClosed(vararg events: EventEmitter<*>) {
    for (event in events) {
        check(!event.hasListeners)
    }
}

