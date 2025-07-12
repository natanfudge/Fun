@file:JvmName("FunKt")

package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.base.FunResource


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
    /*override*/ val id: FunId,
    val parent: Fun? = null,
) : AutoCloseable, Taggable, FunResource {
    val isRoot: Boolean = parent == null

    constructor(parent: Fun, name: String) : this(parent.id.child(name), parent) {
        parent.registerChild(this)
    }

    private val tags = mutableMapOf<String, Any?>()

    override fun <T> getTag(tag: Tag<T>): T? {
        return tags[tag.name] as T?
    }

    override fun removeTag(tag: Tag<*>) {
        tags.remove(tag.name)
    }

    override fun <T> setTag(tag: Tag<T>, value: T?) {
        tags[tag.name] = value
    }

    override fun hasTag(tag: Tag<*>): Boolean {
        return tag.name in tags
    }

//    val closeEvent: EventStream<Unit>
//        field = MutEventStream<Unit>()


    val children = mutableListOf<Fun>()

     fun registerChild(child: Fun) {
        children.add(child)
    }

      fun unregisterChild(child: Fun) {
        children.remove(child)
    }

    private val childCloseables = mutableSetOf<AutoCloseable>()

    override fun alsoClose(closeable: AutoCloseable) {
        childCloseables.add(closeable)
    }

    /**
     * `this` will be closed when this Fun is closed.
     * @return `this`.
     */
    fun AutoCloseable.closeWithThis() = apply { childCloseables.add(this) }

    init {
        context.register(this)
    }

//    private val closeEvent = MutEventStream<Unit>()
//    override fun onClose(callback: () -> Unit): Listener<Unit> = closeEvent.listen { callback() }

    override fun toString(): String {
        return id
    }

    final override fun close() {
        close(unregisterFromParent = true, deleteState = true)
    }

    /**
     * @param unregisterFromParent If true, the parents of this Fun will lose this Fun as a child.
     * @param deleteState If true, the state of this Fun will be deleted.
     * @param unregisterFromContext If true, this Fun will be removed from the context, that includes its state and its place in rootFuns if it had one.
     */
    internal fun close(unregisterFromParent: Boolean,  deleteState: Boolean, unregisterFromContext: Boolean = true,) {
        if (unregisterFromContext) context.unregister(this, deleteState = deleteState)
        childCloseables.forEach { it.close() }
        cleanup()
        // No need to unregister when this is getting closed anyway
        children.forEach { it.close(unregisterFromParent = false, unregisterFromContext = unregisterFromContext, deleteState = deleteState) }
        if (unregisterFromParent) {
            parent?.unregisterChild(this)
        }
    }

    protected open fun cleanup() {

    }

    @Suppress("UNCHECKED_CAST")
    fun <T> storedValue(): T = null as T

}

interface Taggable {
    fun <T> getTag(tag: Tag<T>): T?
    fun <T> setTag(tag: Tag<T>, value: T?)
    fun removeTag(tag: Tag<*>)
    fun hasTag(tag: Tag<*>): Boolean
}

data class Tag<T>(val name: String)

typealias FunId = String
typealias StateId = String

fun FunId.child(name: String) = "$this/$name"