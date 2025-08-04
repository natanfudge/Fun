@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.core

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
    //IDEA: actually, the concepts of cached values and refreshing the app are completely separate.
    // In order to create new cached value when their dependencies change, we can just re-invoke their lambda, without refreshing the app.
    // When a hot reload occurs, we need to refresh the app (at least the parts that changed) to reinitialize possible new fields
    //, but that's done completely independently from creating new cached values.
    // So what we should do, is store the cache lambda themselves, and when their dependency changes
    // (classfile change OR a value change like window resize) we reinvoke them (close + recreate).
    // On hot reload, we still want to refresh the app, but that can just be done after a normal invalidation of caches.
    // The main benefit is it would allow expressing dependencies and having cache values be recreated without refreshing the app.
    // The behavior is the same for the first re-run, stuff will be executed in-line when cached() is first called.
    // On reload, the behavior will be a bit different, they will all close before refreshing as before,
    // HOWEVER all the cached values will be reinitialized together in the order they were previously declared.
    // This has 3 potential issues:
    // 1. Cache init order is swapped:
    // val x by cached {...}
    // val y by cached {...}
    // --- Becomes ---
    // val y by cached {...}
    // val x by cached {f(y)}
    // In this case, x will be initialized first and will use an invalid y value.
    // 2. New cache keys will be ignored.
    // 3. We may use stale values because all the caches run before the actual app.
    // class Foo(val someValue: ImportantClass): NewFun {
    //     val x by cached { f(someValue)}
    //
    // It won't gain the someValue from the current refresh, but from the previous refresh. This is not much of a problem
    // because f() won't expect to have the "newest" value anyway.
    // And TBH it brings out another point where we are often holding stale values in these cached {} functions.
    // Not sure its solvable - we always want to recreate instances and yet we don't want to call f() twice.
    // The usual way this is solved is by having someValue be a mutable variable and then modifying it when it changes.
    // Not sure its possible to do it automatically.

    // Stored in a LinkedHashMap so we can track the order of insertions. We want to init by order, and close by reverse order.
    private val values = LinkedHashMap<CacheKey, CacheValue>()

    /**
     * if [invalidTypes] is null, all state will be deleted
     */
    fun prepareForRefresh(invalidTypes: List<KClass<*>>?) {
        val valueList = values.toList()
        val invalidValues = mutableListOf<AutoCloseable>()
        val invalidTypesSet = invalidTypes?.toSet()
        for ((key, cached) in valueList) {

            // Invalidate because the key was invalidated (directly or through propagation)
            val invalidKey = cached.invalidation.key.invalid
            // Invalidate because the class of the value changed (null has no class)
            val selfClassChanged = cached.value != null && (invalidTypesSet == null || cached.value::class in invalidTypesSet)
            val parentClassChanged = invalidTypesSet == null ||  cached.invalidation.parentClass in invalidTypesSet
            if (invalidKey || selfClassChanged || parentClassChanged) {
                val name = "$key:${cached.value}"
                if (invalidTypesSet == null) {
                    println("Invalidated $name because a full invalidation was requested")
                } else if (invalidKey) {
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
     * Will automatically close the old value and invalidate it, but won't refresh the entire app
     */
    fun <T> set(key: CacheKey, invalidation: InvalidationInfo, value: T) {
        val oldValue = values[key]
        val obj = oldValue?.value
        if (obj is AutoCloseable) {
            obj.close()
        }
        if (obj is InvalidationKey) {
            obj.invalid = true
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