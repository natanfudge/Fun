package io.github.natanfudge.fn.test

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.render.FunRenderState
import kotlinx.coroutines.DelicateCoroutinesApi

//class FunTestDSL

context(v: T) fun <T> ctx() = v

@OptIn(DelicateCoroutinesApi::class)
fun funTest(init: () -> Unit, test: suspend context(FunTestContext) () -> Unit) {
    startTheFun {
//        GlobalScope.launch {
            val ctx = FunTestContext(ctx<FunContext>())
//            with(ctx) {
//                test()
//            }
//        }
    }
//    }
}

class FunTestContext(private val context: FunContext) : FunContext by context {
    /**
     * Asserts that [count] instances of [T] exist in the game.
     *
     * Can use [count] = 0 to assert that no instances exist
     *
     * @return [count] instances of [T] that exist in the game.
     */
    inline fun <reified T : Fun> assertExists(count: Int = 1): List<T> {
        TODO()
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