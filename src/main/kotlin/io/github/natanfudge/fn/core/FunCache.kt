@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.util.Delegate
import io.github.natanfudge.fn.util.obtainPropertyName

class FunCache {
    private val values = mutableMapOf<CacheKey, CacheValue>()

    fun <T> getOrCreate(key: CacheKey, keys: CacheDependencyKeys, ctr: () -> T): T {
        val cached = values[key]
        if (cached == null || keys != cached.keys) {
            val newVal = ctr()
            values[key] = CacheValue(keys, newVal)
            return newVal
        }
        return cached.value as T
    }
}


typealias CacheDependencyKeys = List<Any?>

data class CacheValue(
    val keys: CacheDependencyKeys,
    val value: Any?,
)

typealias CacheKey = String

fun <T : Fun> Fun.memo(vararg keys: Any?, ctr: () -> T): Delegate<T> = obtainPropertyName { name ->
    memo(name, keys.toList(), ctr)
}

fun <T : Fun> Fun.memo(key: String, dependencies: CacheDependencyKeys, ctr: () -> T): T {
    val key = id + key
    return context.cache.getOrCreate(key, dependencies, ctr)
}