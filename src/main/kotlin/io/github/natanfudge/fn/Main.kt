package io.github.natanfudge.fn

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.render.bindFunLifecycles
import io.github.natanfudge.fn.util.restart
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.WindowConfig

// KEEP IN MIND: If we could improve wgpu error handling, we could figure out why we get index out of bounds exceptions when we restart sometimes


const val HOT_RELOAD_SHADERS = true
//fun main() {
//    // We use vsync so don't limit fps
//    val config = WindowConfig(maxFps = Int.MAX_VALUE, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)
//
//    val window = WebGPUWindow()
//    val fsWatcher = FileSystemWatcher()
//    val compose = ComposeWebGPURenderer(config, window, fsWatcher, show = false) { ComposeMainApp() }
//
//    window.bindFunLifecycles(compose, fsWatcher)
//
//    FunHotReload.observation.listen {
//        println("Reload")
//
//
//        // Very important to run this on the main thread
//        window.submitTask {
//            window.surfaceLifecycle.restart(window.window.windowLifecycle.assertValue)
//        }
//
//        window.show(config, callbackHook = compose.callbacks)
//    }
//}

fun main() {
//    val list = mutableListOf<@Composable () -> Unit>()
//    list.add {
//
//    }

    application {
        Window(::exitApplication) {
            Text("gg")
//            list.forEach { it() }
        }
    }
}
//TODO: current workaround i'm thinking of is evicting and recreating the lfiecycle lambdas, and having a try/catch for that one frame where it fails.


// To sum up the current commandments of DCEVM:
// 1. Thou shalt not keep changing code next to long-running code: https://github.com/JetBrains/JetBrainsRuntime/issues/534
// 2. Thou shalt not store a lambda in memory without recreating it on reload: https://github.com/JetBrains/JetBrainsRuntime/issues/535
// 3. Thou shalt not store an anonymous class in memory without recreating it on reload: https://github.com/JetBrains/JetBrainsRuntime/issues/536
// 4. Thou shalt not use inheritance: https://youtrack.jetbrains.com/issue/JBR-8575/After-removing-override-calling-the-invokevirtual-causes-NPE