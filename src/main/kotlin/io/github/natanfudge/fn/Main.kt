package io.github.natanfudge.fn

// KEEP IN MIND: If we could improve wgpu error handling, we could figure out why we get index out of bounds exceptions when we restart sometimes


const val HOT_RELOAD_SHADERS = true
//fun main() {
//    // We use vsync so don't limit fps
//    val config = WindowConfig(maxFps = Int.MAX_VALUE, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)
//
//    val window = WebGPUWindow(config)
//    val fsWatcher = FileSystemWatcher()
//    val compose = ComposeWebGPURenderer(window, fsWatcher, show = false) { ComposeMainApp() }
////    ProcessLifecycle.start(Unit)
////    compose.BackgroundWindowLifecycle.start(Unit)
//    window.setCallbacks(compose.callbacks)
//
//    window.bindFunLifecycles(compose, fsWatcher)
//
//    FunHotReload.observation.listen {
//        println("Reload")
//
//
//        // Very important to run this on the main thread
//        window.submitTask {
//            window.surfaceLifecycle.restart()
//        }
//
//    }
//    ProcessLifecycle.start(Unit)
//    GlfwGameLoop(window.window).loop()
////    window.show(config)
//}


//TODO: current workaround i'm thinking of is evicting and recreating the lfiecycle lambdas, and having a try/catch for that one frame where it fails.


// To sum up the current commandments of DCEVM:
// 1. Thou shalt not keep changing code next to long-running code: https://github.com/JetBrains/JetBrainsRuntime/issues/534
// 2. Thou shalt not store a lambda in memory without recreating it on reload: https://github.com/JetBrains/JetBrainsRuntime/issues/535
// 3. Thou shalt not store an anonymous class in memory without recreating it on reload: https://github.com/JetBrains/JetBrainsRuntime/issues/536
// 4. Thou shalt not use inheritance: https://youtrack.jetbrains.com/issue/JBR-8575/After-removing-override-calling-the-invokevirtual-causes-NPE