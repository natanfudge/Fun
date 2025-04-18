package io.github.natanfudge.fn.window

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * Runs Compose coroutine calls in a controlled fashion, avoiding race conditions. Just giving Compose something like Dispatchers.IO crashes randomly.
 */
class GlfwCoroutineDispatcher : CoroutineDispatcher() {
    private val tasks = mutableListOf<Runnable>()
    private val tasksCopy = mutableListOf<Runnable>()

    /**
     * Needs to be run every frame to dispatch Compose coroutine actions
     */
    fun poll() {
        synchronized(tasks) {
            tasksCopy.addAll(tasks)
            tasks.clear()
        }
        for (runnable in tasksCopy) {
            runnable.run()
        }
        tasksCopy.clear()
    }


    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(tasks) {
            tasks.add(block)
        }
    }
}