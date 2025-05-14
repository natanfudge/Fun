@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.core

import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.GlfwGameLoop
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWErrorCallback
import kotlin.concurrent.atomics.ExperimentalAtomicApi


val RootLifecycles = mutableListOf<Lifecycle<*, *>>()

val ProcessLifecycle = Lifecycle.create<Unit, Unit>("Process") {
    GLFWErrorCallback.createPrint(System.err).set()

    // Initialize GLFW. Most GLFW functions will not work before doing this.
    if (!glfwInit()) {
        throw IllegalStateException("Unable to initialize GLFW")
    }
    it
}

//class ExternalCallback(val callback: () -> Unit, val bo)

val mustRerunLifecycles = mutableSetOf<String>()

/**
 * If called, this lifecycle will always be rerun during hot reload.
 * Use this sparingly - generally you should only use this for re-registering callbacks that might otherwise capture stale objects.
 */
fun <T : Lifecycle<*, *>> T.mustRerun(): T {
    mustRerunLifecycles.add(tree.value.label)
    return this
}

/**
 * How many times the app has been hot-reloaded
 */
var hotReloadIndex = 0

interface FunApp: AutoCloseable {
    fun init(): WebGPUWindow

    fun run() {
        RootLifecycles.add(ProcessLifecycle)
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


                // Recreate lifecycles, workaround for https://github.com/JetBrains/JetBrainsRuntime/issues/535
                val children = ProcessLifecycle.removeChildren()
                // Recreate lifecycles tree
                RootLifecycles.clear()
                RootLifecycles.add(ProcessLifecycle)
                close()
                val newWindow = init()
                loop.window = newWindow.window
                // Copy over old state
                ProcessLifecycle.copyChildrenStateFrom(children)

//            ProcessLifecycle.restartByLabel(ComposeConfig.LifecycleLabel)
                ProcessLifecycle.restartByLabels(mustRerunLifecycles + WebGPUWindow.SurfaceLifecycleLabel)

//                ProcessLifecycle.tree.visitSubtrees {
//                    if (it.value.label in mustRerunLifecycles) Lifecycle<Any, Any>(it).restart()
//                }

                println("Reload17")

            }
        }
        loop.loop()
    }

}




const val HOT_RELOAD_SHADERS = true

