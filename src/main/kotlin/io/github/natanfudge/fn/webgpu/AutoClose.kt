package io.github.natanfudge.fn.webgpu

import kotlin.collections.isNotEmpty
import kotlin.use

interface AutoClose: AutoCloseable {
    val <T : AutoCloseable> T.ac: T

    companion object {
        operator fun <T> invoke(wgpuCode: AutoCloseImpl.() -> T):T {
            return AutoCloseImpl().use {
                it.wgpuCode()
            }
        }
    }
}

fun AutoClose.withUse(usage: AutoClose.() -> Unit) = use(usage)

class AutoCloseImpl: AutoClose {
     val toClose = mutableListOf<AutoCloseable>()

    override val <T : AutoCloseable> T.ac: T
        get() {
            toClose.add(this)
            return this
        }


    override fun close() {
        val closeErrors = mutableListOf<Throwable>()
        for (closeable in toClose) {
            try {
                // Make sure we close everything even if we throw
                closeable.close()
            } catch (e: Throwable) {
                closeErrors.add(e)
            }
        }
        toClose.clear()
        if (closeErrors.isNotEmpty()) throw closeErrors[0]
    }
}

