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
//    val selfClass: KClass<*>,
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
    // Stored in a TreeMap so we can track the order of insertions. We want to init by order, and close by reverse order.

//    /** In general, after refresh, [invalidValues] is very similar to [values] in terms of the type of its content, but with old instances. */
//    private val invalidValues = LinkedHashMap<CacheKey, Any?>()

    //    /**
//     * Classes that had their bytecode changed, so we will make sure to refresh them
//     */
//    private val invalidTypes = mutableListOf<KClass<*>>()
    private val values = LinkedHashMap<CacheKey, CacheValue>()

//    /**
//     * - At the start of a refresh, this is empty.
//     * - Then,  after prepareForRefresh, it contains all values that were not closed.
//     * - Then, after refreshing the app, it contains all values that were not closed or used by the app.
//     * - At that point, we should delete the remaining values as they are unused.
//     */
//    private val limboValues = LinkedHashMap<CacheKey, CacheValue>()


    fun prepareForRefresh(invalidTypes: List<KClass<*>>) {
//        this.invalidTypes.addAll(invalidTypes)
//        limboValues.putAll(values)
//        values.clear()

//        limboValues.putAll(values)
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
                // We will close this now so no need to close it later
//                limboValues.remove(key)
            }
        }

        for (toClose in invalidValues.asReversed()) {
            toClose.close()
        }


//        invalidValues.putAll(values)
//        for ((_, invalid) in invalidValues.toList().asReversed()) {
//            println("Close ${invalid.id}")
//            invalid.cleanupInternal()
//        }
//        values.clear()
    }


    /**
     * Close all unused ('dangling') values
     */
    fun finishRefresh() {
//        for ((key, unused) in limboValues.toList().asReversed()) {
//            val value = unused.value
//            if (value is AutoCloseable) {
//                value.close()
//            }
//            values.remove(key)
//        }
//        limboValues.clear()
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
//        val key = value.id
//        check(key !in values) { "Two Funs were registered with the same ID: $key" }

        // We do not need to close this value as it is being used
//        limboValues.remove(key)
        val cached = values[key]
        if (cached == null) {
            val newValue = ctr()
            values[key] = CacheValue(newValue, invalidation)
            return newValue
        } else {
            return cached.value as T
        }

//        val keys = value.keys
//        if (keys != null && cached != null && keys == cached.keys
//            // Invalidate if any of the keys are invalid as well
//            // Even though invalidValues starts off as all the values, in this case if the key is valid it will not be in the list, because
//            // the key will only be in invalidValues if it has been initialized earlier, e.g.:
//            // | - val a = FunA()
//            // | - FunB(a)
//            // In this case if FunA() is valid, it will be removed from invalidValues and FunB will not invalidate.
//            // If FunA is not removed from invalidValues because it is invalid, then FunB will be invalidated.
//            && keys.none { it is NewFun && it.id in invalidValues }
//            // Make sure to not cache value if its type is invalid
//            && invalidTypes.none { (value as SideEffectFun<*>).type?.qualifiedName == it?.qualifiedName }
//        ) {
//            // Cached value - its not invalid and we don't want to close it or reinitialize it
//            invalidValues.remove(key)
//
//        } else {
//            // New value - we need to initialize it
//            uninitializedValues.add(value)
//            // Key remains invalid in this case, and the value will be closed in finishRefresh()
//        }
//        values[key] = value
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