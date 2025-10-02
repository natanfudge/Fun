package io.github.natanfudge.fn.window

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/**
 * Runs coroutine calls like Compose's in the main thread. Running the coroutines in some arbitrary thread like Dispatchers.IO would lead to race conditions.
 */
class MainThreadCoroutineDispatcher : CoroutineDispatcher() {
    private val queue = Channel<Runnable>(Channel.UNLIMITED)
    @Volatile private var mainThread: Thread? = null


    /**
     * Needs to be run every frame to dispatch Compose coroutine actions
     */
    fun poll() {
        if (mainThread == null) mainThread = Thread.currentThread()

        while (true) {
            val next = queue.tryReceive().getOrNull() ?: break
            next.run()
        }
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        // No need to dispatch if already in the main thread
        return Thread.currentThread() !== mainThread
    }


    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.trySend(block)
    }
    fun addToQueueDirectly(block: Runnable) = queue.trySend(block)
}