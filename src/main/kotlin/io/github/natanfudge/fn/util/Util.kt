package io.github.natanfudge.fn.util

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

fun closeAll(vararg closeables: AutoCloseable?) {
    for (closeable in closeables) {
        // On reload the values might become null
        @Suppress("UNNECESSARY_SAFE_CALL")
        closeable?.close()
    }
}

fun Float.ceilToInt(): Int = ceil(this).toInt()

fun Float.toString(decimalPlaces: Int) = String.format("%.${decimalPlaces}f", this)


fun <K, V> Map<K, V>.withValue(key: K, value: V): Map<K, V> {
    val newMap = toMutableMap()
    newMap[key] = value
    return newMap
}


val PIf = PI.toFloat()

typealias Delegate<T> = PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>>

/**
 * Cool hack to obtain the name of the property that a function is assigned to, for example, in the case
 * `val x = foo()`
 *
 * foo can obtain the String "x" by using obtainPropertyName.
 * The caller will need to use `by` instead of `=` in that case, which is a negligible difference.
 */
@PublishedApi internal fun <T> obtainPropertyName(usage: (String) -> T): Delegate<T> = PropertyDelegateProvider { _, property ->
    val state = usage(property.name) // We don't want to reinitialize it every time it is accessed
    ReadOnlyProperty { _, _ -> state }
}
