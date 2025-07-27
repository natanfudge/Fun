@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.core.newstuff

import androidx.compose.runtime.*
import io.github.natanfudge.fn.compose.utils.composeApp
import kotlinx.coroutines.delay

class FunInitializer {
    // Stored in a TreeMap so we can track the order of insertions. We want to init by order, and close by reverse order.

    /** In general, after refresh, [invalidValues] is very similar to [values] in terms of the type of its content, but with old instances. */
    private val invalidValues = LinkedHashMap<CacheKey, NewFun>()
    /**
     *  [uninitializedValues] is a subset of [values]. Those are values that could not be retrieved from cache and need to be reinitialized.
     *
     *  Note that it's not necessarily true that "[uninitializedValues] = [values] - [invalidValues] as far as the types" because it could be
     *  that on a new refresh a new code path was reached, and therefore there is an additional new type that does not exist in [invalidValues],
     *  and vice versa if an old code path was no longer reached.
     *  If we only close items in [values] that don't exist in [uninitializedValues], we will miss closing some instances that are not present at all anymore in [values].
     *  */
    private val uninitializedValues = mutableListOf<NewFun>()
    private val values = LinkedHashMap<CacheKey, NewFun>()

    /**
     * Refreshing Fun state happens in three stages:
     * 1. Construction: constructors are run, letting us know which values do not need to be closed
     * 2. Close: All objects that were not marked as cached (not removed from the [invalidValues] map) are closed, in reverse order of their construction
     * 3. Init: init() is called for all constructed objects, causing side effects.
     *
     * We run [prepareForRefresh] before stage 1 to have an empty [values] map and track which values are invalid, initially
     * marking all values as invalid. When [requestInitialization] will be called for all components then some will be revalidated, and they won't be closed.
     */
    fun prepareForRefresh() {
        invalidValues.putAll(values)
        values.clear()
    }

    /**
     * Applies stages 2 and 3 of refreshing state, see [prepareForRefresh].
     * Closes all old objects in reverse order and initializes new objects
     * @see prepareForRefresh
     */
    fun finishRefresh() {
        for ((_, invalid) in invalidValues.toList()) {
            invalid.cleanupInternal()
        }
        invalidValues.clear()

        for (value in uninitializedValues) {
            value.init()
        }
        uninitializedValues.clear()
    }





    fun requestInitialization(key: CacheKey, value: NewFun) {
        check(key !in values) { "Two Funs were registered with the same ID: $key" }
        val cached = invalidValues[key]
        val keys = value.keys
        if (keys != null && cached != null && keys == cached.keys) {
            // Cached value - its not invalid and we don't want to close it or reinitialize it
            invalidValues.remove(key)

//            values[key] = value
        } else {
            // New value - we need to initialize it
            uninitializedValues.add(value)
            // Key remains invalid in this case, and the value will be closed in finishRefresh()
//            values[key] = cached
        }
        values[key] = value
    }


    fun remove(key: CacheKey) {
        values.remove(key)
    }
}




class WindowBase {
    fun initialize() {
        println("Initialize window")
    }

    fun close() {
        println("Close WindowBase")
    }
}

class SizedWindow {
    fun initialize() {
        println("Initialize SizedWindow")
    }

    fun close() {
        println("Close SizedWindow")
    }
}

fun main() {
    composeApp {
        var active by remember { mutableStateOf(true) }

        var variable by remember { mutableStateOf(500) }
        LaunchedEffect(Unit) {
//            delay(500)
//            active = false
            delay(500)
            variable = 600
        }
        if (active) {
            val window = key(variable, 200) {
                val window = remember {
                    WindowBase().also { it.initialize() }
                }
                DisposableEffect(Unit) {
                    onDispose { window.close() }
                }
                window
            }
            nested(window)

        }
    }
}

@Composable
fun nested(window: WindowBase) {
    val sizedWindow = key(window) {
        val sizedWindow = remember { SizedWindow().also { it.initialize() } }
        DisposableEffect(Unit) {
            onDispose { sizedWindow.close() }
        }
        sizedWindow
    }
}

interface Fun

@Composable
fun Parent() {

    DisposableEffect(Unit) {
        onDispose {
            println("Parent")
        }
    }
    Child()
}

@Composable
fun Child() {
    DisposableEffect(Unit) {
        onDispose {
            println("Child")
        }
    }
}




typealias CacheKey = String

