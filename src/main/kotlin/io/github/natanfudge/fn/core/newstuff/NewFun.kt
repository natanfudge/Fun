package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.funValue
import io.github.natanfudge.fn.util.Delegate
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.obtainPropertyName
import kotlin.properties.PropertyDelegateProvider


//interface SideEffect : AutoCloseable {

//}

abstract class NewFun internal constructor(
    // Note: we swap the order of parameters here so we can differentiate between this internal constructor and the public one
    val parent: NewFun?,
    /**
     * Unique identifier for this component. Components with the same ID across different clients
     * will synchronize their state.
     */
    val id: FunId,
) : Taggable by TagMap(), Parent<NewFun> by ChildList(), Resource by CloseList(), AutoCloseable {
    constructor(name: String, parent: NewFun = NewFunContextRegistry.getContext().rootFun) : this(parent, parent.id.child(name)) {
        parent.registerChild(this)
    }

    init {
        context.register(this)
    }

    val context: NewFunContext get() = NewFunContextRegistry.getContext()
    inline val events get() = context.events

    open fun init() {

    }

    /**
     * If overridden, [init] will not be called on refresh when none of the [keys] change.
     */
    open val keys: List<Any?>? get() = null


    override fun toString(): String {
        return id
    }

    final override fun close() {
        close(unregisterFromParent = true, deleteState = true, unregisterFromContext = true)
    }

    //TODO:  I don't think we ever want to delete state, that's only relevant when hard restarting, and we can just throw away the state manager.
    /**
     * @param unregisterFromParent If true, the parents of this Fun will lose this Fun as a child.
     * @param deleteState If true, the state of this Fun will be deleted.
     * @param unregisterFromContext If true, this Fun will be removed from the context, that includes its state and its place in rootFuns if it had one.
     */
    internal fun close(unregisterFromParent: Boolean, deleteState: Boolean, unregisterFromContext: Boolean) {
//        if (unregisterFromContext) context.unregister(this, deleteState = deleteState)
        childCloseables.forEach { it.close() }
        cleanup()
        children.forEach { it.close(unregisterFromParent = false, unregisterFromContext = unregisterFromContext, deleteState = deleteState) }
        if (unregisterFromParent) {
            parent?.unregisterChild(this)
        }
    }

    protected open fun cleanup() {

    }

//    fun <T> memo(vararg keys: Any?, ctr: () -> T): Delegate<T> = obtainPropertyName { name ->
//        memo(name, keys.toList(), ctr)
//    }

    inline fun <reified T> funValue(
        initialValue: T?,
        crossinline config: FunValueConfig<T>.() -> Unit = {},
    ): PropertyDelegateProvider<Any, ClientFunValue<T>> = PropertyDelegateProvider { _, property ->
        funValue(initialValue, property.name,  config)
    }


    fun <T> event() = obtainPropertyName {
        // see https://github.com/natanfudge/MineTheEarth/issues/116
        EventEmitter<T>()
    }
}


typealias FunId = String
typealias StateId = String

fun FunId.child(name: String) = "$this/$name"