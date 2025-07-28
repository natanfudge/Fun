package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.FunRememberedValue
import io.github.natanfudge.fn.network.state.FunSet
import io.github.natanfudge.fn.network.state.getFunSerializer
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
    val keys: List<Any?>?,
    autoRegister: Boolean = true,
) : Taggable by TagMap(), Parent<NewFun> by ChildList(), Resource by CloseList(), AutoCloseable {
    constructor(name: String, keys: List<Any?>?, parent: NewFun = NewFunContextRegistry.getContext().rootFun, autoRegister: Boolean = true) :
            this(parent, parent.id.child(name), keys, autoRegister) {
        parent.registerChild(this)
    }

    constructor(name: String, vararg keys: Any?, parent: NewFun = NewFunContextRegistry.getContext().rootFun, autoRegister: Boolean = true) :
    // Treat empty key list as null, as in, always restart
            this(name, keys.let { if (it.isEmpty()) null else it.toList() }, parent, autoRegister)

    init {
        if (autoRegister) {
            context.register(this)
        }
    }

    val context: NewFunContext get() = NewFunContextRegistry.getContext()
    inline val events get() = context.events

    open fun init() {

    }

    override fun toString(): String {
        return id
    }

    final override fun close() {
        close(unregisterFromParent = true)
    }


    /**
     * @param unregisterFromParent If true, the parents of this Fun will lose this Fun as a child.
     * @param unregisterFromContext If true, this Fun will be removed from the context, that includes its state.
     */
    internal fun close(unregisterFromParent: Boolean) {
        context.unregister(this)
        childCloseables.forEach { it.close() }
        cleanup()
        children.forEach { it.close(unregisterFromParent = false) }
        if (unregisterFromParent) {
            parent?.unregisterChild(this)
        }
    }

    /**
     * Accessor to [cleanup] for internal purposes.
     * We want to keep [cleanup] protected.
     */
    internal fun cleanupInternal() = cleanup()

    protected open fun cleanup() {

    }

//    fun <T> memo(vararg keys: Any?, ctr: () -> T): Delegate<T> = obtainPropertyName { name ->
//        memo(name, keys.toList(), ctr)
//    }

    inline fun <reified T> funValue(
        initialValue: T?,
        crossinline config: FunValueConfig<T>.() -> Unit = {},
    ): PropertyDelegateProvider<Any, ClientFunValue<T>> = PropertyDelegateProvider { _, property ->
        funValue({ initialValue }, property.name, config)
    }

    inline fun <reified T> funSet(
        editor: ValueEditor<Set<T>> = ValueEditor.Missing as ValueEditor<Set<T>>, noinline items: () -> MutableSet<T> = { mutableSetOf() },
    ): Delegate<FunSet<T>> = obtainPropertyName {
        funSet(it, getFunSerializer(), items, editor)
    }


    inline fun <reified T> memo(
        noinline initialValue: () -> T?,
    ): PropertyDelegateProvider<Any, FunRememberedValue<T>> = PropertyDelegateProvider { _, property ->
        memo(property.name, typeChecker = { it is T }, initialValue)
    }


    fun <T> event() = obtainPropertyName {
        // see https://github.com/natanfudge/MineTheEarth/issues/116
        EventEmitter<T>()
    }
}


typealias FunId = String
typealias StateId = String

fun FunId.child(name: String) = "$this/$name"