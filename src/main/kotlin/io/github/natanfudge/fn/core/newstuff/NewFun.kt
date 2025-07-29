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
    override val id: FunId,
    val keys: List<Any?>?,
    autoRegister: Boolean = true,
) : Taggable by TagMap(), Parent<NewFun> by ChildList(), Resource, AutoCloseable {
    //    var invalid = false
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

    override val closeAttachments by memo { mutableListOf<AutoCloseable>() }

    override fun alsoClose(closeable: AutoCloseable) {
        closeAttachments.add(closeable)
    }


    /**
     * @param unregisterFromParent If true, the parents of this Fun will lose this Fun as a child.
     * @param unregisterFromContext If true, this Fun will be removed from the context, that includes its state.
     */
    internal fun close(unregisterFromParent: Boolean) {
        context.unregister(this)
        cleanupInternal()
        children.forEach { it.close(unregisterFromParent = false) }
        if (unregisterFromParent) {
            parent?.unregisterChild(this)
        }
    }

    /**
     * Cleans up this Fun along with its [closeAttachments].
     * Does not delete any state, only closes things like listeners and resources, that will be recreated anyway in init().
     * Note that [closeAttachments] != [children].
     * [closeAttachments] Is known to the outside world by this Fun only, its similar to an attachment, that also needs to be cleaned up.
     * [children] Are separate Fun components that are considered children of this [Fun], and because they are [Fun] themselves they will have
     * this function called for them as needed.
     * However, when a USER calls Fun#close, we will call close() for those children as well with unregisterFromParent = false.
     */
    internal fun cleanupInternal() {
        closeAttachments.forEach { it.close() }
        closeAttachments.clear()
        cleanup()
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
        EventEmitter<T>("${this.id}#$it")
    }
}


typealias FunId = String
typealias StateId = String

fun FunId.child(name: String) = "$this/$name"