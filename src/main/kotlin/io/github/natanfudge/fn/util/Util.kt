package io.github.natanfudge.fn.util

import kotlin.math.PI
import kotlin.math.ceil

fun closeAll(vararg closeables: AutoCloseable?) {
    for (closeable in closeables) {
        // On reload the values might become null
        @Suppress("UNNECESSARY_SAFE_CALL")
        closeable?.close()
    }
}
fun Float.ceilToInt(): Int = ceil(this).toInt()

fun Float.toString(decimalPlaces: Int) =String.format( "%.${decimalPlaces}f", this)


fun <K, V> Map<K, V>.withValue(key: K, value: V): Map<K, V> {
    val newMap = toMutableMap()
    newMap[key] = value
    return newMap
}


val PIf = PI.toFloat()
