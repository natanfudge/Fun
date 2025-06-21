package io.github.natanfudge.fn.network

import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.network.state.MapStateHolder
import io.github.natanfudge.fn.physics.Tag
import io.github.natanfudge.fn.physics.Taggable
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.fn.util.MutEventStream


//interface IFun {
//    val id: FunId
//    val context: FunContext
//    fun onClose(callback: () -> Unit): Listener<Unit>
//}


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
    val id: FunId,
    val context: FunContext,
    val parent: Fun? = null
) : AutoCloseable, Taggable {
    val isRoot: Boolean = parent == null

    constructor(parent: Fun, name: String) : this(parent.id.child(name), parent.context, parent) {
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

    internal fun registerChild(child: Fun) {
        children.add(child)
    }

    init {
        context.register(this)
    }

//    private val closeEvent = MutEventStream<Unit>()
//    override fun onClose(callback: () -> Unit): Listener<Unit> = closeEvent.listen { callback() }

    override fun toString(): String {
        return id
    }

    final override fun close() {
        context.unregister(this)
        cleanup()
//        closeEvent.emit(Unit)
        children.forEach { it.close() }
    }

    protected open fun cleanup() {

    }

}

typealias FunId = String

fun FunId.child(name: String) = "$this/$name"