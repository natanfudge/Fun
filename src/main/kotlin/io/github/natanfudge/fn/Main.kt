package io.github.natanfudge.fn

import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.render.FunFixedSizeWindow
import io.github.natanfudge.fn.render.bindFunLifecycles
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.WindowConfig

// KEEP IN MIND: If we could improve wgpu error handling, we could figure out why we get index out of bounds exceptions when we restart sometimes

//TODo: lifecycle refactorings:
// 1. Compose lifecycle
// 2. Webgpu lifecycle
// 3. Pipeline lifecycle
// 4. funRender should include the dimensionLifecycle declaration

const val HOT_RELOAD_SHADERS = true

fun main() {
    // We use vsync so don't limit fps
    val config = WindowConfig(maxFps = Int.MAX_VALUE, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)

    val compose = ComposeWebGPURenderer(config) { ComposeMainApp() }

    val window = WebGPUWindow()
    window.bindFunLifecycles()

//    val surfaceLifecycle by window.surfaceLifecycle
//
//    val dimensionsLifecycle = window.dimensionsLifecycle.bindState("Fun fixed size window") {
//        FunFixedSizeWindow(surfaceLifecycle.device, this)
//    }
//
//    //TODO: after I cleanup the lifecycle stuff, funRender should include the dimensionLifecycle declaration
//    window.init = { window -> funRender(window, compose, dimensionsLifecycle) }

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