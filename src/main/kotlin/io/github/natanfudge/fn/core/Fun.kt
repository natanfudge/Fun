package io.github.natanfudge.fn.core

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.FunRememberedValue
import io.github.natanfudge.fn.network.state.FunSet
import io.github.natanfudge.fn.network.state.getFunSerializer
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Delegate
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.fn.util.obtainPropertyName
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


// https://github.com/natanfudge/Fun/issues/8
abstract class Fun internal constructor(
    override val id: FunId,
    val parent: Fun? = FunContextRegistry.getContext().rootFun,
) : Taggable by TagMap(), Resource, AutoCloseable {

//    override val id: FunId = parent?.id?.child(name) ?: name

    init {
        parent?.registerChild(this)
    }

    companion object {
        const val DEV = true
    }

    init {
        context.register(this)
    }

    val children = mutableListOf<Fun>()

    fun registerChild(child: Fun) {
        children.add(child)
    }

    fun unregisterChild(child: Fun) {
        children.remove(child)
    }

    fun clearChildren() {
        children.clear()
    }

    val context: FunContext get() = FunContextRegistry.getContext()
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

    var closed = false


    /**
     * @param unregisterFromParent If true, the parents of this Fun will lose this Fun as a child.
     * @param unregisterFromContext If true, this Fun will be removed from the context, that includes its state.
     */
    internal fun close(unregisterFromParent: Boolean, deleteState: Boolean) {
        if (deleteState) {
            context.unregister(this)
        }
        for (attachment in closeAttachments) {
            attachment.close()
        }
        cleanup()
        for (child in children) {
            child.close(unregisterFromParent = false, deleteState = deleteState)
        }
        if (unregisterFromParent) {
            parent?.unregisterChild(this)
        }
        closed = true
    }


    protected open fun cleanup() {

    }

    inline fun <reified T> funValue(
        initialValue: T?,
        crossinline config: FunValueConfig<T>.() -> Unit = {},
    ): PropertyDelegateProvider<Any, ClientFunValue<T>> = PropertyDelegateProvider { _, property ->
        funValue(property.name, { initialValue }, config)
    }

    inline fun <reified T> funSet(
        editor: ValueEditor<Set<T>> = ValueEditor.Missing as ValueEditor<Set<T>>, noinline items: () -> MutableSet<T> = { mutableSetOf() },
    ): Delegate<FunSet<T>> = obtainPropertyName {
        funSet(it, getFunSerializer(), items, editor)
    }

    fun addGui(modifier: BoxScope.() -> Modifier = { Modifier }, gui: @Composable BoxScope.() -> Unit): ComposeHudPanel {
        @Suppress("DEPRECATION")
        return context.gui.addUnscopedPanel(modifier, gui).closeWithThis()
    }

    fun addWorldGui(transform: Transform, canvasWidth: Int, canvasHeight: Int, content: (@Composable () -> Unit)) =
        context.gui.addUnscopedWorldPanel(transform, IntSize(canvasWidth, canvasHeight), content).closeWithThis()

    /**
     *
     * CAUTION: Do not store `Fun`s in cached values! Those `Fun`s will become stale as the app refreshes and the instances are reconstructed,
     * but your cached values will not change and still reference those old values.
     * https://github.com/natanfudge/MineTheEarth/issues/120
     *
     *
     * Setting this value will automatically close it if it is [AutoCloseable].
     * If this value is an [IInvalidationKey], Overwriting it will automatically invalidate it.
     * If you want the invalidation to have an effect, you need to refresh the app.
     */
    fun <T> cached(key: IInvalidationKey, ctr: () -> T): PropertyDelegateProvider<Any, CachedValue<T>> = PropertyDelegateProvider { _, property ->
        CachedValue(
            "${this.id}#${property.name}",
            invalidation = InvalidationInfo(key, parentClass = this::class), ctr, context.cache
        )
    }

    // Note: these values are automatically cleaned when a Fun is closed because they are assigned as a part of the entire Fun's
    // entry in the StateManager.
    inline fun <reified T> memo(
        noinline initialValue: () -> T?,
    ): PropertyDelegateProvider<Any, FunRememberedValue<T>> = PropertyDelegateProvider { _, property ->
        memo(property.name, typeChecker = { it is T }, initialValue)
    }

    /**
     * The event listener will be be memoized, so the list of listeners will be kept even when this [Fun] is reconstructed.
     */
    fun <T> event(): PropertyDelegateProvider<Any, FunRememberedValue<EventEmitter<T>>> = PropertyDelegateProvider { _, property ->
        // see https://github.com/natanfudge/MineTheEarth/issues/116
        val name = property.name
        // SUS: Using memo here might not be needed because we restart all listeners on refresh anyway.
        // Although in the future we can def avoid running listeners that are unaffected by classfile changes.
        memo<EventEmitter<T>>(name, typeChecker = { it is EventEmitter<*> }, initialValue = { EventStream.create("${this.id}#$name") })
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