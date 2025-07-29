@file:JvmName("FunKt")

package io.github.natanfudge.fn.core



class CloseList: Resource {
    override val closeAttachments = mutableListOf<AutoCloseable>()
    override val id: FunId
        get() = "old"

    override fun alsoClose(closeable: AutoCloseable) {
        closeAttachments.add(closeable)
    }
}

/**
 * Base class for components that need to synchronize state between multiple clients in a multiplayer environment.
 *
 * Fun components automatically register themselves with a [FunStateManager] and use [funValue] properties
 * to synchronize state changes across all clients.
 *
 * @sample io.github.natanfudge.fn.example.network.NetworkExamples.networkStateExample
 */
abstract class Fun(
    /**
     * Unique identifier for this component. Components with the same ID across different clients
     * will synchronize their state.
     */
    override val id: FunId,
    val parent: Fun? = null,
) : AutoCloseable, Taggable by TagMap(), Parent<Fun> by ChildList<Fun>(), FunResource, Resource by CloseList() {
    companion object {
        val DEV = true
    }
    val isRoot: Boolean = parent == null

    constructor(parent: Fun, name: String) : this(parent.id.child(name), parent) {
        parent.registerChild(this)
    }

    init {
        context.register(this)
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
    internal fun close(unregisterFromParent: Boolean,  deleteState: Boolean, unregisterFromContext: Boolean) {
        if (unregisterFromContext) context.unregister(this, deleteState = deleteState)
        closeAttachments.forEach { it.close() }
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