@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.core.newstuff

import kotlin.reflect.KClass

typealias CacheKey = String

/**
 * IInvalidationKey Serves two purposes:
 * 1. Allow invalidating components in user-land
 * 2. Allows identifying used objects that are invalid, which is a source of a lot of pain.
 */
sealed interface IInvalidationKey {
    var invalid: Boolean

    fun or(other: IInvalidationKey) = ComposedInvalidationKey(this, other)


}

val IInvalidationKey.valid get() = !invalid


class ComposedInvalidationKey(val first: IInvalidationKey, val second: IInvalidationKey) : IInvalidationKey {
    override var invalid: Boolean
        get() = first.invalid && second.invalid
        set(value) {
            first.invalid = value
            second.invalid = value
        }
}

data class CacheValue(
    val value: Any?,
    val invalidation: InvalidationInfo,
) {
    // Internal tracking of propagated invalidations
    var invalid = false
}

data class InvalidationInfo(
    val key: IInvalidationKey,
    /**
     * Often we have cached values stored like this
     * class MyFun : Fun {
     *    val x by cached {...}
     * }
     *
     * In that case, when cached is changed/removed we detect that MyFun changed, but there's no good indication
     * that the specified cached value x changed.
     * So we store an instance of MyFun's class, and when it changes we invalidate x.
     */
    val parentClass: KClass<*>,
)

class FunCache {
    // Stored in a LinkedHashMap so we can track the order of insertions. We want to init by order, and close by reverse order.
    private val values = LinkedHashMap<CacheKey, CacheValue>()

    fun prepareForRefresh(invalidTypes: List<KClass<*>>) {
        val valueList = values.toList()
        val invalidValues = mutableListOf<AutoCloseable>()
        val invalidTypesSet = invalidTypes.toSet()
        for ((key, cached) in valueList) {

            // Invalidate because the key was invalidated (directly or through propagation)
            val invalidKey = cached.invalidation.key.invalid
            // Invalidate because the class of the value changed (null has no class)
            val selfClassChanged = cached.value != null && cached.value::class in invalidTypesSet
            val parentClassChanged = cached.invalidation.parentClass in invalidTypesSet
            if (invalidKey || selfClassChanged || parentClassChanged) {
                val name = "$key:${cached.value}"
                if (invalidKey) {
                    println("Invalidated $name because ${cached.invalidation} invalidated")
                } else if (selfClassChanged) {
                    println("Invalidated $name because its class, ${cached.value::class} changed")
                } else {
                    println("Invalidated $name because its parent class, ${cached.invalidation.parentClass} changed")
                }

                if (cached.value is IInvalidationKey) {
                    cached.value.invalid = true // Propagate invalidation
                }
                if (cached.value is AutoCloseable) {
                    invalidValues.add(cached.value)
                }
                values.remove(key)
            }
        }

        for (toClose in invalidValues.asReversed()) {
            toClose.close()
        }
    }


    /**
     * Will automatically close the old value, but not invalidate it.
     */
    fun <T> set(key: CacheKey, invalidation: InvalidationInfo, value: T) {
        val oldValue = values[key]
        if (oldValue?.value is AutoCloseable) {
            oldValue.value.close()
        }
        values[key] = CacheValue(value, invalidation)
    }


    fun <T> get(key: CacheKey, invalidation: InvalidationInfo, ctr: () -> T): T {
        val cached = values[key]
        if (cached == null) {
            val newValue = ctr()
            values[key] = CacheValue(newValue, invalidation)
            return newValue
        } else {
            return cached.value as T
        }
    }


    fun remove(key: FunId) {
        values.remove(key)
    }
}


abstract class InvalidationKey : AutoCloseable, IInvalidationKey {
    override var invalid = false
    override fun close() {

    }

    object None : IInvalidationKey {
        override var invalid: Boolean = false
    }
}