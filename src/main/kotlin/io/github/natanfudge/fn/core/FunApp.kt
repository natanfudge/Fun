@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.GlfwGameLoop
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWErrorCallback
import kotlin.concurrent.atomics.ExperimentalAtomicApi



val ProcessLifecycle = Lifecycle.create<Unit, Unit>("Process") {
    GLFWErrorCallback.createPrint(System.err).set()

    // Initialize GLFW. Most GLFW functions will not work before doing this.
    if (!glfwInit()) {
        throw IllegalStateException("Unable to initialize GLFW")
    }
    it
}


/**
 * How many times the app has been hot-reloaded
 */
var hotReloadIndex = 0

interface FunApp : AutoCloseable {
    fun init(): WebGPUWindow

    fun run() {
        val window = init()
        val loop = GlfwGameLoop(window.window)

        ProcessLifecycle.start(Unit)


        FunHotReload.reloadStarted.listen {
            println("Reload started: pausing app")

            loop.locked = true
        }


        FunHotReload.reloadEnded.listen {
            // This has has special handling because it needs to run while the gameloop is locked
            loop.reloadCallback = {
                hotReloadIndex++
                println("Reloading app")

                ProcessLifecycle.removeChildren()

                close()
                init()

                try {
                    ProcessLifecycle.restartByLabels(setOf(WebGPUWindow.SurfaceLifecycleLabel))
                } catch (e: Throwable) {
                    println("Failed to perform a granular restart, trying to restart the app entirely")
                    e.printStackTrace()
                    ProcessLifecycle.restart()
                }
                println("Reload19")

            }
        }
        loop.loop()
    }

}


const val HOT_RELOAD_SHADERS = true

