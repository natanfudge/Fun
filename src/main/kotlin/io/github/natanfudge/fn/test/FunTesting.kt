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
import kotlin.time.Duration

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
     * Asserts that exactly [count] instances of [T] are visible on the screen.
     *
     * @return The instances of [T] that are visible (exactly [count]).
     */
    inline fun <reified T : Fun> assertVisibleExactly(count: Int): List<T> {
        val ofType = getFuns<T>()
        val visibleFuns = ofType.filter { isVisible(it) }
        if (visibleFuns.size != count) {
            throw AssertionError("Expected exactly $count visible ${T::class.simpleName} but found ${visibleFuns.size}: ${visibleFuns.map { it.id }}")
        }
        return visibleFuns
    }

    /**
     * Asserts that at least [count] instances of [T] are visible on the screen.
     *
     * @return The instances of [T] that are visible (at least [count]).
     */
    inline fun <reified T : Fun> assertVisibleAtLeast(count: Int): List<T> {
        val ofType = getFuns<T>()
        val visibleFuns = ofType.filter { isVisible(it) }
        if (visibleFuns.size < count) {
            throw AssertionError("Expected at least $count visible ${T::class.simpleName} but found ${visibleFuns.size}: ${visibleFuns.map { it.id }}")
        }
        return visibleFuns
    }

    /**
     * Asserts that at most [count] instances of [T] are visible on the screen.
     *
     * @return The instances of [T] that are visible (no more than [count]).
     */
    inline fun <reified T : Fun> assertVisibleAtMost(count: Int): List<T> {
        val ofType = getFuns<T>()
        val visibleFuns = ofType.filter { isVisible(it) }
        if (visibleFuns.size > count) {
            throw AssertionError("Expected at most $count visible ${T::class.simpleName} but found ${visibleFuns.size}: ${visibleFuns.map { it.id }}")
        }
        return visibleFuns
    }

    /**
     * Asserts that between [min] and [max] instances of [T] are visible on the screen (inclusive).
     *
     * @return The instances of [T] that are visible (between [min] and [max], inclusive).
     */
    inline fun <reified T : Fun> assertVisibleBetween(min: Int, max: Int): List<T> {
        require(min <= max) { "min ($min) must be less than or equal to max ($max)" }
        val ofType = getFuns<T>()
        val visibleFuns = ofType.filter { isVisible(it) }
        if (visibleFuns.size !in min..max) {
            throw AssertionError("Expected between $min and $max visible ${T::class.simpleName} but found ${visibleFuns.size}: ${visibleFuns.map { it.id }}")
        }
        return visibleFuns
    }

    /**
     * Asserts that instances of [T] are visible on the screen.
     * This means that they have a child [FunRenderState], that is being rendered to the screen with at least one pixel of it visible from the perspective
     * and not hidden by other objects.
     *
     * @return The instances of [T] that are visible (at least one).
     */
    inline fun <reified T : Fun> assertVisible(): List<T> {
        val ofType = getFuns<T>()
        val visibleFuns = ofType.filter { isVisible(it) }
        if (visibleFuns.isEmpty()) {
            val totalCount = ofType.size
            if (totalCount == 0) {
                throw AssertionError("No ${T::class.simpleName} instances exist at all, so none are visible as required.")
            } else {
                throw AssertionError("No ${T::class.simpleName} instances are visible. Found $totalCount instances but none are visible: ${ofType.map { it.id }}")
            }
        }
        return visibleFuns
    }

    /**
     * Asserts that no instances of [T] are visible on the screen.
     * This means that either no instances exist, or they exist but are not visible.
     */
    inline fun <reified T : Fun> assertNotVisible() {
        val ofType = getFuns<T>()
        val visibleFuns = ofType.filter { isVisible(it) }
        if (visibleFuns.isNotEmpty()) {
            throw AssertionError("Expected no ${T::class.simpleName} instances to be visible, but found ${visibleFuns.size} visible instances: ${visibleFuns.map { it.id }}. ${if (ofType.size > visibleFuns.size) "There are also ${ofType.size - visibleFuns.size} non-visible instances." else ""}")
        }
    }

    /**
     * Asserts that all items of this list are visible on the screen.
     * This means that they have a child [FunRenderState], that is being rendered to the screen with at least one pixel of it visible from the perspective
     * and not hidden by other objects.
     *
     * @throws AssertionError if any item in the list is not visible
     */
    fun <T : Fun> List<T>.assertVisible() {
        if (isEmpty()) {
            throw AssertionError("Cannot assert visibility on an empty list. The list contains no items to check.")
        }

        val nonVisibleItems = filter { !isVisible(it) }
        if (nonVisibleItems.isNotEmpty()) {
            val visibleCount = size - nonVisibleItems.size
            throw AssertionError("Expected all ${size} items to be visible, but ${nonVisibleItems.size} items are not visible: ${nonVisibleItems.map { it.id }}. $visibleCount items are visible.")
        }
    }

    /**
     * Asserts that none of the items in this list are visible on the screen.
     *
     * @throws AssertionError if any item in the list is visible
     */
    fun <T : Fun> List<T>.assertNotVisible() {
        if (isEmpty()) {
            throw AssertionError("Cannot assert visibility on an empty list. The list contains no items to check.")
        }

        val visibleItems = filter { isVisible(it) }
        if (visibleItems.isNotEmpty()) {
            val nonVisibleCount = size - visibleItems.size
            throw AssertionError("Expected all ${size} items to be not visible, but ${visibleItems.size} items are visible: ${visibleItems.map { it.id }}. $nonVisibleCount items are correctly not visible.")
        }
    }

    @PublishedApi internal fun isVisible(fn: Fun): Boolean {
        TODO()
    }

    fun simulateInput(input: InputEvent) {
    }

    /**
     * Assert that the visual content of the screen remains exactly the same as it was in previous calls with the same [scene].
     *
     * Use [scene] to differentiate between different scenes. For example, all invocations with scene `A`, must look the same,
     * but they can be different from scene `B`. [scene] is namespaced to the current test, so you can use the same scene name across multiple tests,
     * and it will refer to different actual scenes.
     *
     * The first time this assertion will run it will pass no matter what, and will store the window content for later runs of the test.
     * On subsequent calls, it will check the window content matches precisely pixel-by-pixel to what it was beforehand.
     *
     * The expected image is stored at TODO.
     * In order to reset the test to allow a new image, simply delete its expected image at TODO
     *
     * Benefits:
     * - EXTREMELY effective at finding bugs, especially those that require the human eye to detect, or are hard to detect even by humans.
     * - EXTREMELY easy to setup, there is practically 0 effort to call this.
     *
     * Downsides:
     * - This is a "freeze test" meaning it does not test anything the first time you write it, but rather
     * it only becomes useful after making changes.
     * - It is extremely flaky, meaning even the slightest change will break the test. This isn't so bad since you
     * can check what changed, and if it's okay you just re-freeze the scene.
     * - If something is determined by a random process it will break every time. To solve this, run the relevant things
     * with a predetermined seed, making sure your app is deterministic based on the seed. This is generally good advice for testing.
     *
     */
    fun assertSightRemainsTheSame(scene: String = "") {

    }

    /**
     * Same as [assertSightRemainsTheSame] but compares only the part of the screen containing this [Fun].
     * This makes it a lot more robust than [assertSightRemainsTheSame] because it won't break if other parts of the scene change,
     * so you should prefer this one if possible.
     *
     * @see assertSightRemainsTheSame
     */
    fun Fun.assertLooksTheSame(scene: String = "") {

    }

    /**
     * Assert that the way this [Fun] looks matches [description], using an LLM.
     *
     * This will attempt to take a screenshot of purely this [Fun], searching for a render child of it to find in the screen and capture.
     * A portion of the window will be captured that is only the part containing [Fun]. The resulting image will be sent to an LLM,
     * and it will decide whether it matches the given [description]. If not, an error is thrown and the LLM will describe why it does not match the description.
     *
     * Will throw early if this [Fun] is not visible on the screen, so asserting on [assertVisible] first is not necessary.
     *
     * For asserting on multiple things on once, [assertAppearanceWithLLM] will be more efficient but might be less robust.
     * */
    fun Fun.assertLooksLikeWithLLM(description: String) {

    }

    /**
     * Assert that the current screen content matches [description], using an LLM.
     *
     * This will take a screenshot, and ask an LLM if it matches the [description]. If not, this will throw and the LLM will explain why it does not match the description.
     * This is very powerful but expensive.
     *
     * For asserting only on one thing, prefer [assertAppearanceWithLLM].
     * For asserting on something that needs to be viewed across multiple frames, use [assertAppearanceWithLLM].
     */
    fun assertSightWithLLM(description: String) {

    }

    /**
     * Assert that the screen content for the next [duration], sampling [fps] times per second, matches [description], using an LLM.
     *
     * This will take multiple screenshots over the course of the [duration], with the delay between them determined by [fps].
     * A video will be composed with those screenshots and sent to an LLM to determine if it matches the [description]. If not, this will throw and the LLM will explain why the video does not match the description.
     *
     * This is very powerful but expensive, and might not be totally accurate, depending on the [fps] and the quality of the LLM.
     */
    fun assertAnimationWithLLM(description: String, duration: Duration, fps: Int) {

    }

}