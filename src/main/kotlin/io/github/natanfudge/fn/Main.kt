package io.github.natanfudge.fn

import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.render.funRender
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.WindowConfig

// KEEP IN MIND: If we could improve wgpu error handling, we could figure out why we get index out of bounds exceptions when we restart sometimes


const val HOT_RELOAD_SHADERS = true

fun main() {
    // We use vsync so don't limit fps
    val config = WindowConfig(maxFps = Int.MAX_VALUE, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)

    val compose = ComposeWebGPURenderer(config) { ComposeMainApp() }


    val window = WebGPUWindow(
        init = { window -> funRender(window, compose) },
    )

    FunHotReload.observation.listen {
        println("Reload")
        window.submitTask {
            // Very important to run this on the main thread
            window.restart(config)
            compose.restart()
        }
    }


    window.show(config)
}