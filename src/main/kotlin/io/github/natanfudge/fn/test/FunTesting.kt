package io.github.natanfudge.fn.test

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.mte.Block
import io.github.natanfudge.fn.mte.DeepSouls
import io.github.natanfudge.fn.render.FunRenderState
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//class FunTestDSL

context(v: T) fun <T> ctx() = v

/**
 * Will start a [Fun] app and run [test], and then close the app.
 *
 * When hot reloading, [test] will be cancelled and re-run.
 */
@OptIn(DelicateCoroutinesApi::class)
fun funTest(test: suspend FunTestContext.() -> Unit) {
    startTheFun {
        TestFun(test)
    }
}

//TODO:
// 2. Uncomment Main.kt and adjust to new APIs, and rename it to "FunTestApp.kt"
// 3. Make it so our test case simply launches the test app, delay(5.seconds),  and closes.
// 4. Understand what exactly is being test in FunTestApp, and add more assertion definitions to FunTestContext. Need to think how we can test visual things better. Maybe AI.
// 5. Implement assertions & simulated controls
// 6. Completely test the test app
// 7. Add some more test cases from DeepSouls
// 8. Implement GPU memory freeing
// 9. Test memory freeing by having a small max buffer and deleting & recreating stuff, and then checking if stuff looks correct
// 10. Implement GPU memory resizing (For some things)
// 11. Test resizing by starting with a small buffer and adding a lot of things, and then checking if stuff looks correct.

fun main() {
    funTest {
        assertExists<TestFun>()
        DeepSouls()
        assertExists<DeepSouls>()

        delay(10000)
        assertExists<Block>()
    }
}

class TestFun(val callback: suspend FunTestContext.() -> Unit) : Fun("TestFun") {
    val scope = coroutineScope()

    init {
        scope.launch {
            try {
                FunTestContext(this@TestFun).callback()
            } catch (e: Throwable) {
                e.printStackTrace()
                stopApp()
            }
            stopApp()
        }
    }
}

class FunTestContext(private val context: FunContext) : FunContext by context {
    /**
     * Asserts that instances of [T] exist in the game.
     *
     * @return Instances of [T] that exist in the game (at least one).
     */
    inline fun <reified T : Fun> assertExists(): List<T> {
        val ofType = getFuns<T>()
        if (ofType.isEmpty()) {
            throw AssertionError("No ${T::class.simpleName} exist (expected at least one)")
        }
        return ofType
    }

    /**
     * Asserts that no instances of [T] exist in the game.
     */
    inline fun <reified T : Fun> assertDoesNotExist() {
        val ofType = getFuns<T>()
        if (ofType.isNotEmpty()) {
            throw AssertionError("${ofType.size} ${T::class.simpleName} exist (expected none): ${ofType.map { it.id }}")
        }
    }

    /**
     * Asserts that exactly [count] instances of [T] exist in the game.
     *
     * @return The instances of [T] that exist (exactly [count]).
     */
    inline fun <reified T : Fun> assertExistExactly(count: Int): List<T> {
        val ofType = getFuns<T>()
        if (ofType.size != count) {
            throw AssertionError("Expected exactly $count ${T::class.simpleName} but found ${ofType.size}: ${ofType.map { it.id }}")
        }
        return ofType
    }

    /**
     * Asserts that at least [count] instances of [T] exist in the game.
     *
     * @return The instances of [T] that exist (at least [count]).
     */
    inline fun <reified T : Fun> assertExistAtLeast(count: Int): List<T> {
        val ofType = getFuns<T>()
        if (ofType.size < count) {
            throw AssertionError("Expected at least $count ${T::class.simpleName} but found ${ofType.size}: ${ofType.map { it.id }}")
        }
        return ofType
    }

    /**
     * Asserts that at most [count] instances of [T] exist in the game.
     *
     * @return The instances of [T] that exist (no more than [count]).
     */
    inline fun <reified T : Fun> assertExistAtMost(count: Int): List<T> {
        val ofType = getFuns<T>()
        if (ofType.size > count) {
            throw AssertionError("Expected at most $count ${T::class.simpleName} but found ${ofType.size}: ${ofType.map { it.id }}")
        }
        return ofType
    }

    /**
     * Asserts that between [min] and [max] instances of [T] exist in the game (inclusive).
     *
     * @return The instances of [T] that exist (between [min] and [max], inclusive).
     */
    inline fun <reified T : Fun> assertExistBetween(min: Int, max: Int): List<T> {
        require(min <= max) { "min ($min) must be less than or equal to max ($max)" }
        val ofType = getFuns<T>()
        if (ofType.size !in min..max) {
            throw AssertionError("Expected between $min and $max ${T::class.simpleName} but found ${ofType.size}: ${ofType.map { it.id }}")
        }
        return ofType
    }

    @PublishedApi
    internal inline fun <reified T : Fun> getFuns() = getAllFuns().filterIsInstance<T>()

    @PublishedApi
    internal fun getAllFuns(): List<Fun> {
        val funs = mutableListOf<Fun>()
        aggregateFuns(rootFun, funs)
        return funs
    }

    private fun aggregateFuns(start: Fun, to: MutableList<Fun>) {
        to.add(start)
        for (child in start.children) {
            aggregateFuns(child, to)
        }
    }

    /**
     * Asserts that [count] instances of [T] are visible on the screen.
     * This means that they have a child [FunRenderState], that is being rendered to the screen with at least one pixel of it visible from the perspective
     * and not hidden by other objects.
     *
     * Can use [count] = 0 to assert that no instances are visible.
     */
    inline fun <reified T : Fun> assertVisible(count: Int = 1) {

    }

    /**
     * Asserts that all items of this list are visible on the screen.
     * This means that they have a child [FunRenderState], that is being rendered to the screen with at least one pixel of it visible from the perspective
     * and not hidden by other objects.
     */
    fun <T : Fun> List<T>.assertVisible() {

    }

    fun simulateInput(input: InputEvent) {
    }

}