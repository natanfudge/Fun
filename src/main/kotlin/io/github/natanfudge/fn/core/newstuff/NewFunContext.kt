package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.FunCache
import io.github.natanfudge.fn.core.FunContext

class NewFunContext {
    val cache = FunCache()
}

internal object NewFunContextRegistry {
    private lateinit var context: FunContext
    fun setContext(context: FunContext) {
        this.context = context
    }

    fun getContext() = context
}

fun main() {
    val context = NewFunContext()

}