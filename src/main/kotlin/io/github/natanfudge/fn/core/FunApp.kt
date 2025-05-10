package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.render.bindFunLifecycles
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.GlfwGameLoop
import io.github.natanfudge.fn.window.WindowConfig
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWErrorCallback



val ProcessLifecycle = Lifecycle.create<Unit, Unit>("Process") {
    GLFWErrorCallback.createPrint(System.err).set()

    // Initialize GLFW. Most GLFW functions will not work before doing this.
    if (!glfwInit()) {
        throw IllegalStateException("Unable to initialize GLFW")
    }
    it
}

class BaseFunApp {
    fun init(): WebGPUWindow {
        // We use vsync so don't limit fps
        val config = WindowConfig(maxFps = Int.MAX_VALUE, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)

        val window = WebGPUWindow(config)
        val fsWatcher = FileSystemWatcher()
        val compose = ComposeWebGPURenderer(window, fsWatcher, show = false) { ComposeMainApp() }
        window.setCallbacks(compose.callbacks)

        window.bindFunLifecycles(compose, fsWatcher)



        return window
    }
}

fun main() {
    val app = BaseFunApp()
    val window = app.init()
    val loop = GlfwGameLoop(window.window)

    ProcessLifecycle.start(Unit)

    //TODO: 1. Figure out how we can lock frames during reload to avoid many errors
    // 2. Copy over old data to new children
    // 3. Restart what we want
    //
    FunHotReload.observation.listen {
        // Very important to run this on the main thread
        loop.window.submitTask {
            // Recreate lifecycles, workaround for https://github.com/JetBrains/JetBrainsRuntime/issues/535
            val children = ProcessLifecycle.removeChildren()
            val newWindow = app.init()
            children.forEach { it.end() }

            loop.window = newWindow.window

            ProcessLifecycle.start(Unit)
            println("Reload3")


//            loop.window.submitTask {
//                loop.window.surfaceLifecycle.restart()
//            }
        }

    }
    loop.loop()
}

