@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.util

import io.github.natanfudge.fn.error.UnfunStateException
import io.github.natanfudge.fn.webgpu.AutoClose
import io.github.natanfudge.fn.webgpu.AutoCloseImpl
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

enum class FunLogLevel(val value: Int) {
    Verbose(0),
    Debug(1),
    Info(2),
    Warn(3),
    Error(4)
}

//TODO: completely rework where we have a graph holding the graph data and then start() just iterates through the graph and calls the registered callbacks

class BindableLifecycleBuilder {
    internal var _onDestroy: (() -> Unit)? = null

    fun onDestroy(callback: () -> Unit) {
        _onDestroy = callback
    }
}

//NICETOHAVE: cool lifecycle tree visualization

val activeLogLevel = FunLogLevel.Debug

 inline fun log(level: FunLogLevel, msg: () -> String) {
    if (level.value >= activeLogLevel.value) {
        println(msg())
    }
}

data class LifecycleChild<P : Any>(val index: Int, val lifecycle: Lifecycle<P>)

class BindableLifecycle<P : Any, I : Any>(val lifecycle: StatefulLifecycle<P, I>) : StatefulLifecycle<P, I> {
    companion object {
        /**
         * Causes [callback] to be called whenever this lifecycle is birthed.
         * [callback] must return a value that may be used by other parts of the code by calling `bind*{}` on the result of this function.
         * If [R] is [AutoCloseable], it will be automatically closed when `this` lifecycle dies.
         */
        fun <I : Any, R : Any> createRoot(
            label: String,
            logLevel: FunLogLevel = FunLogLevel.Debug,
            callback: BindableLifecycleBuilder.(I) -> R,
        ): BindableLifecycle<I, R> {
            val custom = object : StatefulLifecycleImpl<I, R>() {
                val builder = BindableLifecycleBuilder()


                override fun toString(): String {
                    return label
                }

                override fun create(parent: I): R {
                    log(logLevel) { "Start of $this" }
                    return callback(builder, parent)
                }

                override fun destroy(obj: R) {
                    log(logLevel) { "End of $this" }
                    if (obj is AutoCloseable) {
                        obj.close()
                    }
                    if (builder._onDestroy != null) {
                        builder._onDestroy!!()
                    }
                }
            }
            val bindable = BindableLifecycle(custom)

            return bindable
        }
    }

//    private var values =


    private val children = mutableListOf<LifecycleChild<I>>()
    private var childrenValues: List<*>? = null

    override fun toString(): String {
        return lifecycle.toString()
    }

    //  TODO: compress all the features into this one class - closing, logging, and holding statem

    override fun start(parent: P, parentIndex: Int) {
        create(parent, parentIndex)
    }

    override fun end() {
        if (state != null) {
            destroy(state!!)
        } else {
            println("Warning: '$this' destroyed before it could be constructed")
        }
    }

    override var state: I? = null

    private fun create(parentValue: P, parentIndex: Int) {
        lifecycle.start(parentValue, parentIndex)
        val state = lifecycle.state
        if (state != null) { // It's possible that this lifecycle wants start() to be called multiple times with different parent indices for it to be ready
            this.state = state
//        val selfValue = lifecycle.create(parentValue)

            var error: Throwable? = null
            try {
                childrenValues = children.map {
                    try {
                        it.lifecycle.start(state, it.index)
                        if (it.lifecycle is StatefulLifecycle<I, *>) {
                            it.lifecycle.state
                        } else {
                            Unit
                        }
                    } catch (e: Throwable) {
                        error = e
                        null // Set the value to 'null' to note it was not initialized
                    }
                }
                // Don't swallow errors, it was just delayed so the other children could run normally
                if (error != null) throw error
            } catch (e: ConcurrentModificationException) {
                throw UnfunStateException(
                    "A ConcurrentModificationException happened during lifecycle creation, this is usually a result of binding to a lifecycle during its initialization",
                    e
                )
            }

        }

    }

    fun bind(cycle: Lifecycle<I>, parentIndex: Int = 0) {
        children.add(LifecycleChild(parentIndex, cycle))
    }

    fun bindHighPriority(cycle: Lifecycle<I>, parentIndex: Int = 0) {
        children.add(0, LifecycleChild(parentIndex, cycle))
    }

    fun unbind(cycle: Lifecycle<I>) {
        children.removeAll { it.lifecycle == cycle }
    }

    private fun destroy(selfValue: I) {
        if (childrenValues == null) {
            println("Warning: ManagedLifecycle${this} closed before it was started successfully")
        } else {
            // Kill children, then kill parent
            children.zip(childrenValues!!).forEach { (child, value) ->
                if (value == null) {
                    println("Warning: Skipping death of Lifecycle $child as it did not birth properly.")
                } else {
//                } else if (child is StatefulLifecycle<*, *>) {
//                    (child as StatefulLifecycle<*, Any?>).destroy(value)
//                } else {
                    child.lifecycle.end()
                }
            }
        }

        lifecycle.end()
    }
}

/**
 * Causes [callback] to be called whenever this lifecycle is birthed.
 */
inline fun <T : Any> BindableLifecycle<*, T>.bind(label: String? = null, crossinline callback: (T) -> Unit): Lifecycle<T> {
    val lifecycle = object : Lifecycle<T> {
        override fun toString(): String {
            return label ?: "unnamed callback"
        }

        override fun start(parent: T, parentIndex: Int) {
            println("Running '$this' for birth of '${this@bind}'")
            callback(parent)
        }

        override fun end() {
        }
    }
    bind(lifecycle)
    return lifecycle
}

/**
 * Causes [callback] to be called whenever this lifecycle is birthed, before all other callbacks.
 */
fun <T : Any> BindableLifecycle<*, T>.bindHighPriority(label: String = "unnamed callback", logLevel: FunLogLevel, callback: (T) -> Unit): Lifecycle<T> {
    val lifecycle = object : Lifecycle<T> {
        override fun toString(): String {
            return label
        }

        override fun start(parent: T, parentIndex: Int) {
            log(logLevel) { "Running '$this' for birth of '${this@bindHighPriority}'" }
            callback(parent)
        }

        override fun end() {
        }
    }
    bindHighPriority(lifecycle)
    return lifecycle
}

/**
 * Causes [callback] to be called whenever this lifecycle is birthed.
 * [callback] must return a function that will be called before this lifecycle dies.
 */
inline fun <T : Any> BindableLifecycle<*, T>.bindCloseable(
    label: String? = null,
    logLevel: FunLogLevel = FunLogLevel.Debug,
    crossinline callback: AutoClose.(T) -> (() -> Unit)
): StatefulLifecycle<T, () -> Unit> {
    val autoclose = AutoCloseImpl()
    val lifecycle = object : StatefulLifecycleImpl<T, () -> Unit>() {
        override fun toString(): String {
            return label ?: "unnamed closeable"
        }

        override fun create(parent: T): () -> Unit {
            log(logLevel) { "Running initialization of '$this' for birth of ${this@bindCloseable}" }
            return autoclose.callback(parent)
//            return with(callback) {
//                autoclose.call(parent)
//            }
//            return autoclose.callback(parent)
        }

        override fun destroy(destruction: () -> Unit) {
            log(logLevel) {
                "Running destruction of '$this' for death of ${this@bindCloseable}"
            }
            destruction()
            autoclose.close()
        }
    }
    bind(lifecycle)
    return lifecycle
}

fun interface AutoCloseCallback<T, R> {
    fun AutoClose.call(value: T): R
}
//TODO: note why we do inline here
inline fun <T : Any> BindableLifecycle<*, T>.bindAutoclose(
    label: String? = null,
    logLevel: FunLogLevel = FunLogLevel.Debug,
    crossinline callback: AutoClose.(T) -> Unit,
): StatefulLifecycle<T, () -> Unit> {
    return bindCloseable(label, logLevel) {
        callback(it);
//        with(callback) {
//            call(it)
//        };
//        callback.call(it);
        {}
    }
}

/**
 * Causes [callback] to be called whenever this lifecycle is birthed.
 * [callback] must return a value that may be used by other parts of the code by calling `bind*{}` on the result of this function.
 * If [R] is [AutoCloseable], it will be automatically closed when `this` lifecycle dies.
 */
fun <T : Any, R : Any> BindableLifecycle<*, T>.bindBindable(
    label: String = "unnamed bindable",
    logLevel: FunLogLevel = FunLogLevel.Debug,
    callback: BindableLifecycleBuilder.(T) -> R,
): BindableLifecycle<T, R> {
    val bindable = BindableLifecycle.createRoot(label, logLevel, callback)
    bind(bindable)
    return bindable
}

//TODO: refactor/document
fun <P1 : Any, P2 : Any, R : Any> BindableLifecycle<*, P1>.bindBindableTwoParents(
    parent2: BindableLifecycle<*, P2>,
    label: String = "unnamed bindable",
    logLevel: FunLogLevel = FunLogLevel.Debug,
    callback: BindableLifecycleBuilder.(P1, P2) -> R,
): BindableLifecycle<Any, R> {

    val custom = object : StatefulLifecycleImpl<Pair<P1, P2>, R>() {
        val builder = BindableLifecycleBuilder()


        override fun toString(): String {
            return label
        }

        override fun create(parent: Pair<P1, P2>): R {
            log(logLevel) { "Start of $this" }
            return callback(builder, parent.first, parent.second)
        }

        override fun destroy(obj: R) {
            log(logLevel) { "End of $this" }
            if (obj is AutoCloseable) {
                obj.close()
            }
            if (builder._onDestroy != null) {
                builder._onDestroy!!()
            }
        }
    }
    val lifecycle = TwoParentLifecycle(custom)
    val bindable = BindableLifecycle(lifecycle)

    this.bind(bindable as Lifecycle<P1>)
    parent2.bind(bindable as Lifecycle<P2>, parentIndex = 1)
    return bindable
}

//TODo: this shouldn't exist when we have multiple parent dependencies
/**
 * Causes [callback] to be called whenever this lifecycle is birthed, and before all other bound lifecycles.
 * [callback] must return a value that may be used by other parts of the code by calling `bind*{}` on the result of this function.
 * If [R] is [AutoCloseable], it will be automatically closed when `this` lifecycle dies.
 */
fun <T : Any, R : Any> BindableLifecycle<*, T>.bindHighPriorityBindable(
    label: String = "unnamed bindable",
    logLevel: FunLogLevel = FunLogLevel.Debug,
    callback: BindableLifecycleBuilder.(T) -> R,
): BindableLifecycle<T, R> {
    val bindable = BindableLifecycle.createRoot(label, logLevel, callback)
    bindHighPriority(bindable)
    return bindable
}

/**
 * Causes [callback] to be called whenever this lifecycle is birthed.
 * [callback] must return a value that may be used by other parts of the code by calling `use()`
 */
fun <T : Any, R : Any> BindableLifecycle<*, T>.bindState(label: String? = null, callback: AutoClose.(T) -> R): StatefulLifecycle<T, R> {
    val ac = AutoCloseImpl()
    val lifecycle = object : StatefulLifecycleImpl<T, R>() {
        override fun toString(): String {
            return label ?: "unnamed stateful"
        }

        override fun create(parent: T): R {
            println("Running initialization of '$this' for birth of ${this}")
            return ac.callback(parent)
        }

        override fun destroy(obj: R) {
            if (obj is AutoCloseable) {
                println("Running destruction of '$this' for death of ${this}")
                obj.close()
                ac.close()
            }
        }
    }
    bind(lifecycle)
    return lifecycle
}



//NOTE: could consider having a GeneralLifecycle that just has fun performStage(stage) and then we have performStage(birth) performStage(death)
// but it could be more general to include intermediate stages
interface Lifecycle<Parent : Any> {
    fun start(parent: Parent, parentIndex: Int = 0)
    fun end()
}
//TODO: I gotta reduce the size of the stack trace somehow

interface StatefulLifecycle<Parent : Any, S : Any> : Lifecycle<Parent>, ReadOnlyProperty<Any?, S> {
    val state: S?

    val isCreated get() = state != null

    val assertValue get() = state!!

    override fun getValue(thisRef: Any?, property: KProperty<*>): S {
        return state ?: error("Attempt to get state of '$this' before it was initialized")
    }
}

abstract class StatefulLifecycleImpl<Parent : Any, S : Any> : StatefulLifecycle<Parent, S> {
    abstract fun create(parent: Parent): S

    abstract fun destroy(state: S)

    override var state: S? = null


    final override fun start(parent: Parent, parentIndex: Int) {
        state = create(parent)
    }

    final override fun end() {
        if (state != null) {
            destroy(state!!)
        } else {
            println("Warning: '$this' destroyed before it could be constructed")
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): S {
        return state ?: error("Attempt to get state of '$this' before it was initialized")
    }
}


class TwoParentLifecycle<P1 : Any, P2 : Any, S : Any>(val wrapped: StatefulLifecycle<Pair<P1, P2>, S>) : StatefulLifecycle<Any, S> {
    private var parent1State: P1? = null
    private var parent2State: P2? = null


    override var state: S? = null

//    override val state: S? = null

    override fun start(parent: Any, parentIndex: Int) {
        if (parentIndex == 0) {
            parent1State = parent as P1
        } else if (parentIndex == 1) {
            parent2State = parent as P2
        } else {
            error("TwoParentLifecycle does not support parent index $parent2State")
        }
        val state1 = parent1State
        val state2 = parent2State
        if (state1 != null && state2 != null) {
            wrapped.start((state1 to state2), 0)
            this.state = wrapped.assertValue
        }
    }

    override fun end() {
        wrapped.end()
    }

}

//TODo: better document what parentIndex means
fun <T : Any> Lifecycle<T>.restart(value: T, parentIndex: Int = 0) {
    end()
    start(value, parentIndex)
}

//TODO: in the future we could do this because we'll store the parent's value in the node
//fun <T : Any> StatefulLifecycle<T, *>.restart(value: T = assertValue) {
//    end()
//    start(value)
//}