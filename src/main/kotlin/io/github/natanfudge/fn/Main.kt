package io.github.natanfudge.fn

import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.compose.GlfwComposeWindow
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.render.FunFixedSizeWindow
import io.github.natanfudge.fn.render.bindFunLifecycles
import io.github.natanfudge.fn.util.restart
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.WindowConfig

// KEEP IN MIND: If we could improve wgpu error handling, we could figure out why we get index out of bounds exceptions when we restart sometimes


const val HOT_RELOAD_SHADERS = true

//TODO: fix ui not being visible during resize
fun main() {
    // We use vsync so don't limit fps
    val config = WindowConfig(maxFps = Int.MAX_VALUE, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)

    val window = WebGPUWindow()
    val fsWatcher = FileSystemWatcher()
    val compose = ComposeWebGPURenderer(config, window,fsWatcher, show = false) { ComposeMainApp() }

    window.bindFunLifecycles(compose, fsWatcher)


    FunHotReload.observation.listen {
        println("Reload")


        // Very important to run this on the main thread
        window.submitTask {
            window.surfaceLifecycle.restart(window.window.windowLifecycle.assertValue)
//            window.restart(config)
//            compose.restart()
        }
    }


    window.show(config, callbackHook = compose.callbacks)
}