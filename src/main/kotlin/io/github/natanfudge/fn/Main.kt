package io.github.natanfudge.fn

import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.render.bindFunLifecycles
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.WindowConfig

// KEEP IN MIND: If we could improve wgpu error handling, we could figure out why we get index out of bounds exceptions when we restart sometimes


class BaseFunApp: FunApp {
    private lateinit var fsWatcher: FileSystemWatcher
    override fun init(): WebGPUWindow {
        // We use vsync so don't limit fps
        val config = WindowConfig(maxFps = Int.MAX_VALUE, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)

        val window = WebGPUWindow(config)

        fsWatcher = FileSystemWatcher()

        val compose = ComposeWebGPURenderer(window, fsWatcher, show = false) { ComposeMainApp() }
//        window.window.callbacks.clear()

        window.bindFunLifecycles(compose, fsWatcher)


        return window
    }

    override fun close() {
        fsWatcher.close()
    }


}

fun main() {
    BaseFunApp().run()
}



