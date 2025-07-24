package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.util.Delegate
import io.github.natanfudge.fn.util.obtainPropertyName


abstract class NewFun(
    /**
     * Unique identifier for this component. Components with the same ID across different clients
     * will synchronize their state.
     */
    val id: FunId,
    val parent: NewFun? = null,
) : AutoCloseable, Taggable by TagMap(), Parent<NewFun> by ChildList(), Resource by CloseList() {
    val context get() = NewFunContextRegistry.getContext()
    val isRoot: Boolean = parent == null

    constructor(parent: NewFun, name: String) : this(parent.id.child(name), parent) {
        parent.registerChild(this)
    }

    init {
//        context.register(this)
    }

    fun <T> memo(vararg keys: Any?, ctr: () -> T): Delegate<T> = obtainPropertyName { name ->
        memo(name, keys.toList(), ctr)
    }

    fun <T> memo(key: String, dependencies: CacheDependencyKeys, ctr: () -> T): T {
        val key = id + key
        return context.cache.getOrCreate(key, dependencies, ctr)
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
}


typealias FunId = String
typealias StateId = String

fun FunId.child(name: String) = "$this/$name"