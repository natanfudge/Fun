package io.github.natanfudge.fn.window

import io.github.natanfudge.fn.core.ProcessLifecycle

/**
 * The [window] needs to be updated whenever it is recreated.
 */
class GlfwGameLoop(val window: GlfwWindowConfig) {
    var reloadCallback: (() -> Unit)? = null

    private val currentWindow get() = window.windowLifecycle.assertValue

    fun loop() {
        while (currentWindow.open) {
            checkForReloads()
            if (!window.eventPollLifecycle.isInitialized) window.eventPollLifecycle.start(Unit)
            else {
                try {
                    window.eventPollLifecycle.restart()
                } catch (e: Throwable) {
                    val hotReload = window.HotReloadSave.value
                    if (hotReload?.restarted == true) {
                        println("Crashed in poll after making a hot reload, trying to restart app")
                        e.printStackTrace()
                        hotReload.restarted = false
                        ProcessLifecycle.restart()
                    } else {
                        throw e
                    }
                }
            }
            checkForReloads()
            if (!currentWindow.open) break
            if (currentWindow.minimized) continue
            val time = System.nanoTime()
            val delta = time - currentWindow.lastFrameTimeNano
            if (delta >= 1e9 / currentWindow.params.maxFps) {
                checkForReloads()
                currentWindow.pollTasks()
                checkForReloads()
                window.frameLifecycle.restart()
            }
        }
    }


    private fun checkForReloads() {
        if (reloadCallback != null) {
            reloadCallback!!()
            reloadCallback = null
        }
    }
}