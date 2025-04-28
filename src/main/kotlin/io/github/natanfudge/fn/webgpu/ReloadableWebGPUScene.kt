package io.github.natanfudge.fn.webgpu

import io.github.natanfudge.fn.Restartable
import io.github.natanfudge.fn.hotreload.FunHotReload

class ReloadableWebGPUScene(val window: WebGPUWindow, val setup: AutoClose.(WebGPUWindow) -> Unit): Restartable<Unit>, AutoCloseable {
    val restartScope = AutoClose()
    fun show() {
        with(restartScope) {
            setup(window)
        }
        window.display()
    }


    override fun restart(params: Unit?) {
        window.clearFrameQueue()
        restartScope.close()
        with(restartScope) {
            setup(window)
        }
    }

    override fun close() {
        restartScope.close()
        window.close()
    }
}

fun WebGPUWindow.reloadable(init: AutoClose.(WebGPUWindow) -> Unit)  {
    val window = ReloadableWebGPUScene(this, init)

    FunHotReload.observation.listen {
        println("Reloading WebGPU scene")
        window.window.submitTask {
            // Very important to run this on the main thread
            window.restart()
        }
    }
    window.show()
}