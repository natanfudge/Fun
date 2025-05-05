@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.util

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

//TODO: add autoclosing to this, maybe as a later on top because we do have die()
class ProcessState : AutoCloseable {
    val someComplexCalculation = 123
    override fun close() {
        println("Auto-closing process")
    }
}

class ProcessLifecycle : StatefulLifecycle<Unit, ProcessState>() {
    override fun toString(): String {
        return "Process"
    }

    override fun create(parent: Unit): ProcessState {
        println("Process start")
        val state = ProcessState()
        return state
    }

    override fun destroy(state: ProcessState) {
        println("Process end")
    }
}

//class WindowState {
//    val handle  = 123456
//}
//class WindowLifecycle: StatefulLifecycle<ProcessState, WindowState> {
//    override fun birth(parent: ProcessState): WindowState {
//        println("Creating window")
//        return WindowState()
//    }
//
//    override fun die(state: WindowState) {
//        println("Deleting window")
//    }
//}

class LifecycleBuilder {
    internal var _onDestroy: (() -> Unit)? = null
    fun onDestroy(callback: () -> Unit) {
        _onDestroy = callback
    }
}

//TODO: a big thing i need to think about is how to get lifecycles to access each other

//TODO: think how we can log birth/death properly
class BindableLifecycle<P : Any, I : Any>(val lifecycle: StatefulLifecycle<P, I>) : StatefulLifecycle<P, I>() {
    companion object {
        /**
         * Causes [callback] to be called whenever this lifecycle is birthed.
         * [callback] must return a value that may be used by other parts of the code by calling `bind*{}` on the result of this function.
         * If [R] is [AutoCloseable], it will be automatically closed when `this` lifecycle dies.
         */
        fun <I : Any, R : Any> createRoot(label: String, callback: LifecycleBuilder.(I) -> R): BindableLifecycle<I, R> {
            val builder = LifecycleBuilder()
            val lifecycle = object : StatefulLifecycle<I, R>() {
                override fun toString(): String {
                    return label
                }

                override fun create(parent: I): R {
                    println("Start of $this")
                    return callback(builder, parent)
                }

                override fun destroy(obj: R) {
                    println("End of $this")
                    if (obj is AutoCloseable) {
                        obj.close()
                    }
                    if (builder._onDestroy != null) {
                        builder._onDestroy!!()
                    }
                }
            }
            return BindableLifecycle(lifecycle)
        }
    }

    private val children = mutableListOf<Lifecycle<I>>()
    private var childrenValues: List<*>? = null
//    private var selfValue: I? = null

    override fun toString(): String {
        return lifecycle.toString()
    }

    override fun create(parentValue: P): I {
        val selfValue = lifecycle.create(parentValue)
//        this.selfValue = selfValue
        var error: Throwable? = null
        childrenValues = children.map {
            try {
                it.start(selfValue)
                if (it is StatefulLifecycle<I, *>) {
                    it.state
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
        return selfValue
    }

    // TODO: maybe need unbind?
    fun bind(cycle: Lifecycle<I>) {
        children.add(cycle)
    }

    override fun destroy(selfValue: I) {
        if (childrenValues == null) {
            println("Warning: ManagedLifecycle${this} closed before it was started successfully")
        } else {
            // Kill children, then kill parent
            children.zip(childrenValues!!).forEach { (child, value) ->
                if (value == null) {
                    println("Warning: Skipping death of Lifecycle $child as it did not birth properly.")
                } else if (child is StatefulLifecycle<*, *>) {
                    (child as StatefulLifecycle<*, Any?>).destroy(value)
                } else {
                    child.end()
                }
            }
        }

        lifecycle.destroy(selfValue)
    }

}

/**
 * Causes [callback] to be called whenever this lifecycle is birthed.
 */
fun <T : Any> BindableLifecycle<*, T>.bind(label: String? = null, callback: T.() -> Unit) {
    bind(object : Lifecycle<T> {
        override fun toString(): String {
            return label ?: "unnamed callback"
        }

        override fun start(parent: T) {
            println("Running '$this' for birth of '${this@bind}'")
            callback(parent)
        }

        override fun end() {
        }

    })
}

/**
 * Causes [callback] to be called whenever this lifecycle is birthed.
 * [callback] must return a function that will be called before this lifecycle dies.
 */
fun <T : Any> BindableLifecycle<*, T>.bindCloseable(label: String? = null, callback: T.() -> (() -> Unit)) {
    bind(object : StatefulLifecycle<T, () -> Unit>() {
        override fun toString(): String {
            return label ?: "unnamed closeable"
        }

        override fun create(parent: T): () -> Unit {
            println("Running initialization of '$this' for birth of ${this@bindCloseable}")
            return callback(parent)
        }

        override fun destroy(destruction: () -> Unit) {
            println("Running destruction of '$this' for death of ${this@bindCloseable}")
            destruction()
        }
    })
}

//TODO: nice method of creating root lifecycles
/**
 * Causes [callback] to be called whenever this lifecycle is birthed.
 * [callback] must return a value that may be used by other parts of the code by calling `bind*{}` on the result of this function.
 * If [R] is [AutoCloseable], it will be automatically closed when `this` lifecycle dies.
 */
fun <T : Any, R : Any> BindableLifecycle<*, T>.bindBindable(label: String? = null, callback: T.() -> R): BindableLifecycle<T, R> {
    val lifecycle = object : StatefulLifecycle<T, R>() {
        override fun toString(): String {
            return label ?: "unnamed bindable"
        }

        override fun create(parent: T): R {
            println("Running initialization of '$this' for birth of ${this@bindBindable}")
            return callback(parent)
        }

        override fun destroy(obj: R) {
            if (obj is AutoCloseable) {
                println("Running destruction of '$this' for death of ${this@bindBindable}")
                obj.close()
            }
        }
    }
    val bindable = BindableLifecycle(lifecycle)
    bind(bindable)
    return bindable
}

/**
 * Causes [callback] to be called whenever this lifecycle is birthed.
 * [callback] must return a value that may be used by other parts of the code by calling `use()`
 */
fun <T : Any, R : Any> BindableLifecycle<*, T>.bindState(label: String? = null, callback: T.() -> R): StatefulLifecycle<T, R> {
    val lifecycle = object : StatefulLifecycle<T, R>() {
        override fun toString(): String {
            return label ?: "unnamed stateful"
        }

        override fun create(parent: T): R {
            println("Running initialization of '$this' for birth of ${this@bindState}")
            return callback(parent)
        }

        override fun destroy(obj: R) {
            if (obj is AutoCloseable) {
                println("Running destruction of '$this' for death of ${this@bindState}")
                obj.close()
            }
        }
    }
    bind(lifecycle)
    return lifecycle
}


//private inline fun String?.labelOr(ifNull: () -> String) = if(this == null) ifNull() else "'$this'"


object BuiltinLifecycles {
    val Process = BindableLifecycle.createRoot<Unit, ProcessState>("Process") {
        ProcessState()
    }
}

class MyProcessLifecycleState(val parent: ProcessState) {
    val customData = 987

}


fun main() {
    val myLifecycle = BuiltinLifecycles.Process.bindBindable("Custom lifecycle") {
        MyProcessLifecycleState(this)
    }
    myLifecycle.bind {
        println("Child custom lifecycle born, accessing grantparent ${parent.someComplexCalculation}")
    }
    myLifecycle.bind {
        println("Doing some custom inner shit with $customData")
    }
    myLifecycle.bindCloseable("The greatest closeable") {
        println("Constructing something externally");
        {
            println("Destroying it")
        }
    }
    BuiltinLifecycles.Process.start(Unit)
    BuiltinLifecycles.Process.end()
}

//TODO: could consider having a GeneralLifecycle that just has fun performStage(stage) and then we have performStage(birth) performStage(death)
// but it could be more general to include intermediate stages
interface Lifecycle<Parent : Any> {
    fun start(parent: Parent)
    fun end()
}


abstract class StatefulLifecycle<Parent : Any, S : Any> : Lifecycle<Parent>, ReadOnlyProperty<Any?, S> {
    abstract fun create(parent: Parent): S

    abstract fun destroy(state: S)

    var state: S? = null

    final override fun start(parent: Parent) {
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

//class Val<T: Any>(private val lifecycle: StatefulLifecycle<*,T>) : ReadOnlyProperty<Any?, T> {
//    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
//        return lifecycle.state ?: error("Attempt to get state of $this@use before it was initialized")
//    }
//}
//
//fun <T : Any> StatefulLifecycle<*, T>.use() = Val(this)


fun <T : Any> Lifecycle<T>.restart(value: T) {
    end()
    start(value)
}