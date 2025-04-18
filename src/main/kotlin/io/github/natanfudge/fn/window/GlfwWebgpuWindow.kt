package io.github.natanfudge.fn.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.compose.GlInitComposeGlfwAdapter
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.system.MemoryUtil.NULL


class GlfwWebgpuWindow {
    private lateinit var instance: GlInitGlfwWindowInstance
    private var open = true

    private var windowPos: IntOffset? = null

    @OptIn(InternalComposeUiApi::class)
    private inner class GlInitGlfwWindowInstance(config: WindowConfig) {
        val waitingTasks = mutableListOf<() -> Unit>()

        init {
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Initially invisible to give us time to move it to the correct place
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
            glfwWindowHint(GLFW_FLOATING, GLFW_TRUE) // Focus window on open
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE) // For macOS compatibility
        }

        val windowHandle = glfwCreateWindow(
            config.initialWindowWidth, config.initialWindowHeight, config.initialTitle, NULL, NULL
        )

        init {
            if (windowHandle == NULL) {
                glfwTerminate()
                throw RuntimeException("Failed to create the GLFW window")
            }
            glfwMakeContextCurrent(windowHandle)
            GL.createCapabilities()

            glfwSetWindowCloseCallback(windowHandle) {
                open = false
            }

            if (windowPos != null) {
                // Keep the window in the same position when reloading
                glfwSetWindowPos(windowHandle, windowPos!!.x, windowPos!!.y)
            }
            glfwShowWindow(windowHandle)
            glfwSetWindowPosCallback(windowHandle) { _, x, y ->
                windowPos = IntOffset(x, y)
            }

            glfwSetWindowSizeCallback(windowHandle) { _, windowWidth, windowHeight ->
                compose.resize(windowWidth, windowHeight)
                render() // We want to content to adapt faster to resize changes so we rerender right away.
            }
        }

        val dispatcher = GlfwCoroutineDispatcher()

        val compose = GlInitComposeGlfwAdapter(
            config.initialWindowWidth, config.initialWindowHeight, windowHandle,dispatcher,
            density = Density(glfwGetWindowContentScale(windowHandle)),
            composeContent = { ComposeMainApp() }
        )

        fun close() {
            compose.close()
            glfwSetWindowCloseCallback(windowHandle, null)
            glfwDestroyWindow(windowHandle)
        }

    }

    /**
     * Submits a callback to run on the main thread.
     */
    fun submitTask(task: () -> Unit) {
        instance.waitingTasks.add(task)
    }


    fun show(config: WindowConfig = WindowConfig()) {
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        instance = GlInitGlfwWindowInstance(config)

        while (open) {
            glfwPollEvents()
            with(instance) {
                dispatcher.poll()
                if (compose.invalid) {
                    render()
                }
                waitingTasks.forEach { it() }
                waitingTasks.clear()
            }
//            Thread.sleep(30)// Temporary hack
        }
        instance.close()
    }

    fun render() = with(instance) {
        compose.draw()
        GLFW.glfwSwapBuffers(windowHandle)
        compose.invalid = false
    }


    fun restart(config: WindowConfig = WindowConfig()) {
        instance.close()
        instance = GlInitGlfwWindowInstance(config)
    }
}


private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    GLFW.glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}