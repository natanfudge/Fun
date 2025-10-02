package io.github.natanfudge.fn.test.app

import io.github.natanfudge.fn.test.funTest
import kotlin.test.Test

class TestTestApp {
    @Test
    fun testTestApp() {
        funTest {
            FunTestApp()
            assertExistAtLeast<TestBody>(4)
            assertExistAtLeast<TestRenderObject>(4)
        }
    }
}