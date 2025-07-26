@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.core.newstuff

import androidx.compose.runtime.*
import io.github.natanfudge.fn.compose.utils.composeApp
import kotlinx.coroutines.delay

class FunInitializer {
    // Stored in a TreeMap so we can track the order of insertions. We want to init by order, and close by reverse order.
    private val invalidValues = LinkedHashMap<CacheKey, CacheValue>()
    private val values = LinkedHashMap<CacheKey, CacheValue>()

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
        invalidValues.toList().asReversed().forEach { (_, value) ->
            value.value.close(unregisterFromParent = false, deleteState = false, unregisterFromContext = false)
        }
        values.forEach { (_, value) ->
            value.value.init()
        }
        invalidValues.clear()
    }


    fun requestInitialization(key: CacheKey, keys: CacheDependencyKeys, value: NewFun) {
        check(key !in values) { "Two Funs were registered with the same ID: $key" }
        val cached = invalidValues[key]

        if (keys == null || cached == null || keys != cached.dependencies) {
            // Key remains invalid in this case, and the value will be closed in finishRefresh()
            values[key] = CacheValue(keys, value)
        } else {
            values[key] = cached
            // Cached value - its not invalid and we don't want to close it
            invalidValues.remove(key)
        }
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

typealias CacheDependencyKeys = List<Any?>?

data class CacheValue(
    val dependencies: CacheDependencyKeys,
    val value: NewFun,
)

typealias CacheKey = String

