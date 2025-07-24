@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.Fun
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

