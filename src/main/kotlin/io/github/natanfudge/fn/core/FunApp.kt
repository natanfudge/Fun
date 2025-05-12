@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.core

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.natanfudge.fn.compose.ComposeConfig
import io.github.natanfudge.fn.compose.ComposeGlfwWindow
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
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi

//val RootLifecycles = Lifecycle.create<Unit, Unit>("Root"){it}

val RootLifecycles = mutableListOf<Lifecycle<*,*>>()

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
//        window.setCallbacks(/*compose.callbacks*/)
        window.window.setCallbacks(compose.callbacks)
//
        window.bindFunLifecycles(compose, fsWatcher)


        return window
    }
}



//TODO: assertion `left == right` failed: Sampler[Id(0,1)] is no longer alive crashrino when reloading and then resizing window


fun main() {
    val app = BaseFunApp()
    RootLifecycles.add(ProcessLifecycle)
    val window = app.init()
    val loop = GlfwGameLoop(window.window)

    ProcessLifecycle.start(Unit)


    FunHotReload.reloadStarted.listen {
        println("Reload started: pausing app")

        loop.locked = true
    }

    FunHotReload.reloadEnded.listen {
        // This has has special handling because it needs to run while the gameloop is locked
        loop.reloadCallback = {
            println("Reloading app")




            // Recreate lifecycles, workaround for https://github.com/JetBrains/JetBrainsRuntime/issues/535
            val children = ProcessLifecycle.removeChildren()
            // Recreate lifecycles tree
            RootLifecycles.clear()
            RootLifecycles.add(ProcessLifecycle)
            val newWindow = app.init()
            loop.window = newWindow.window
            // Copy over old state
            ProcessLifecycle.copyChildrenStateFrom(children)

//            ProcessLifecycle.restartByLabel(ComposeConfig.LifecycleLabel)
            ProcessLifecycle.restartByLabel(WebGPUWindow.SurfaceLifecycleLabel)

            println("Reload17")

        }
    }
    loop.loop()
}

