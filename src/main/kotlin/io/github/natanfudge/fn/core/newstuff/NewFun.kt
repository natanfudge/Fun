package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.core.Resource
import io.github.natanfudge.fn.core.TagMap
import io.github.natanfudge.fn.core.Taggable
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.FunRememberedValue
import io.github.natanfudge.fn.network.state.FunSet
import io.github.natanfudge.fn.network.state.getFunSerializer
import io.github.natanfudge.fn.util.Delegate
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.obtainPropertyName
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


abstract class NewFun internal constructor(
    // Note: we swap the order of parameters here so we can differentiate between this internal constructor and the public one
    val parent: NewFun?,
    /**
     * Unique identifier for this component. Components with the same ID across different clients
     * will synchronize their state.
     */
    override val id: FunId,
) : Taggable by TagMap(), Resource, AutoCloseable {
    constructor(name: String, parent: NewFun = NewFunContextRegistry.getContext().rootFun) :
            this(parent, parent.id.child(name)) {
        parent.registerChild(this)
    }

    init {
        context.register(this)
    }

    val children = mutableListOf<NewFun>()

    fun registerChild(child: NewFun) {
        children.add(child)
    }

    fun unregisterChild(child: NewFun) {
        children.remove(child)
    }

    fun clearChildren() {
        children.clear()
    }

    val context: NewFunContext get() = NewFunContextRegistry.getContext()
    inline val events get() = context.events

    override fun toString(): String {
        return id
    }

    final override fun close() {
        close(unregisterFromParent = true, deleteState = true)
    }

    final override val closeAttachments = mutableListOf<AutoCloseable>()

    final override fun alsoClose(closeable: AutoCloseable) {
        closeAttachments.add(closeable)
    }


    /**
     * @param unregisterFromParent If true, the parents of this Fun will lose this Fun as a child.
     * @param unregisterFromContext If true, this Fun will be removed from the context, that includes its state.
     */
    internal fun close(unregisterFromParent: Boolean, deleteState: Boolean) {
        if (deleteState) context.unregister(this)
        for (attachment in closeAttachments) {
            attachment.close()
        }
        cleanup()
        children.forEach { it.close(unregisterFromParent = false, deleteState = deleteState) }
        if (unregisterFromParent) {
            parent?.unregisterChild(this)
        }
    }


    protected open fun cleanup() {

    }

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

    /**
     *
     * CAUTION: Do not store `Fun`s in cached values! Those `Fun`s will become stale as the app refreshes and the instances are reconstructed,
     * but your cached values will not change and still reference those old values.
     *  //TODO: eventually, an inspection that forbids this would make sense.
     *
     *
     * Setting this value will automatically close it if it is [AutoCloseable].
     * If this value is an [IInvalidationKey], Setting it will not automatically invalidate it.
     * For that, you can set [IInvalidationKey.invalid] to true yourself, and request a refresh.
     * (NOTE: maybe add a flag to do that, because that's sometimes desirable)
     */
    fun <T> cached(key: IInvalidationKey, ctr: () -> T): PropertyDelegateProvider<Any, CachedValue<T>> = PropertyDelegateProvider { _, property ->
        CachedValue(
            "${this.id}#${property.name}",
            invalidation = InvalidationInfo(key, parentClass = this::class), ctr, context.cache
        )
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

class CachedValue<T>(val key: String, val invalidation: InvalidationInfo, val init: () -> T, val cache: FunCache) : ReadWriteProperty<Any?, T> {
    // Avoid talking to the cache whenever we want to get the value
    private var _value = cache.get(key, invalidation, init)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return _value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        _value = value
        cache.set(key, invalidation, value)
    }

}


typealias FunId = String
typealias StateId = String

fun FunId.child(name: String) = "$this/$name"