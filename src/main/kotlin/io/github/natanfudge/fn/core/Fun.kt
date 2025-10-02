@file:OptIn(UnfunAPI::class)

package io.github.natanfudge.fn.core

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.network.state.ClientFunValue
import io.github.natanfudge.fn.network.state.FunList
import io.github.natanfudge.fn.network.state.FunRememberedValue
import io.github.natanfudge.fn.network.state.FunSet
import io.github.natanfudge.fn.network.state.getFunSerializer
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.Delegate
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.fn.util.obtainPropertyName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


private class ClosedFunCoroutineScopeException(id: FunId) : CancellationException("Fun $id was closed")

// https://github.com/natanfudge/MineTheEarth/issues/119
abstract class Fun internal constructor(
    override val id: FunId,
    val parent: Fun? = FunContextRegistry.getContext().rootFun,
    val _ctx: FunContext = FunContextRegistry.getContext()
) : Taggable by TagMap(), Resource, AutoCloseable, FunContext by _ctx {

    init {
        parent?.registerChild(this)
    }

    companion object {
        const val DEV = true
    }

    init {
        register(this)
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
     * Returns a coroutine scope that is bound to this [Fun], meaning it will be automatically canceled when this [Fun] is closed.
     */
    fun coroutineScope() : CoroutineScope {
        val scope = CoroutineScope(mainThreadCoroutineContext)

        alsoClose {
            //TODO: properly test this mechanism
            scope.cancel(ClosedFunCoroutineScopeException(id))
        }
        return scope
    }

    var closed = false


    /**
     * @param unregisterFromParent If true, the parents of this Fun will lose this Fun as a child.
     */
    internal fun close(unregisterFromParent: Boolean, deleteState: Boolean) {
        if (deleteState) {
            unregister(this)
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

    inline fun <reified T> funList(
        editor: ValueEditor<List<T>> = ValueEditor.Missing as ValueEditor<List<T>>, noinline items: () -> MutableList<T> = { mutableListOf() },
    ): Delegate<FunList<T>> = obtainPropertyName {
        funList (it, getFunSerializer(), items, editor)
    }

    fun addGui(modifier: BoxScope.() -> Modifier = { Modifier }, guiCode: @Composable BoxScope.() -> Unit): ComposeHudPanel {
        @Suppress("DEPRECATION")
        return gui.addUnscopedPanel(modifier, guiCode).closeWithThis()
    }

    fun addWorldGui(transform: Transform, canvasWidth: Int, canvasHeight: Int, content: (@Composable () -> Unit)) =
        gui.addUnscopedWorldPanel(transform, IntSize(canvasWidth, canvasHeight), content).closeWithThis()

    /**
     *
     * CAUTION: Do not store `Fun`s in cached values! Those `Fun`s will become stale as the app refreshes and the instances are reconstructed,
     * but your cached values will not change and still reference those old values.
     * https://github.com/natanfudge/MineTheEarth/issues/120
     *
     * Storing [FunContext] is okay because that object is eternal.
     *
     *
     * Setting this value will automatically close it if it is [AutoCloseable].
     * If this value is an [IInvalidationKey], Overwriting it will automatically invalidate it.
     * If you want the invalidation to have an effect, you need to refresh the app.
     */
    fun <T> cached(key: IInvalidationKey, ctr: () -> T): PropertyDelegateProvider<Any, CachedValue<T>> = PropertyDelegateProvider { _, property ->
        CachedValue(
            "${this.id}#${property.name}",
            invalidation = InvalidationInfo(key, parentClass = this::class), ctr, cache
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