package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.util.Delegate
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.obtainPropertyName


interface SideEffect : AutoCloseable {
    fun init() {}
}

abstract class NewFun internal constructor(
    // Note: we swap the order of parameters here so we can differentiate between this internal constructor and the public one
    val parent: NewFun?,
    /**
     * Unique identifier for this component. Components with the same ID across different clients
     * will synchronize their state.
     */
    val id: FunId,
) :  Taggable by TagMap(), Parent<NewFun> by ChildList(), Resource by CloseList(), SideEffect {
    val context get() = NewFunContextRegistry.getContext()
    inline val events get() = context.events

    constructor(name: String, parent: NewFun = NewFunContextRegistry.getContext().rootFun) : this(parent, parent.id.child(name)) {
        parent.registerChild(this)
    }

    init {
//        context.register(this)
    }


    override fun toString(): String {
        return id
    }

    final override fun close() {
        close(unregisterFromParent = true, deleteState = true, unregisterFromContext = true)
    }

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

    fun <T> memo(vararg keys: Any?, ctr: () -> T): Delegate<T> = obtainPropertyName { name ->
        memo(name, keys.toList(), ctr)
    }

    fun <T> event() = obtainPropertyName {
        // see https://github.com/natanfudge/MineTheEarth/issues/116
        EventEmitter<T>()
    }
}


typealias FunId = String
typealias StateId = String

fun FunId.child(name: String) = "$this/$name"