package io.github.natanfudge.fu.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import io.github.natanfudge.fu.compose.ComposeGlfwAdapter
import kotlinx.coroutines.delay
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.system.MemoryUtil.NULL

class GlfwWebgpuWindow {
    private var windowHandle: Long = 0
    private var open = true


    private val waitingTasks = mutableListOf<() -> Unit>()

    /**
     * Submits a callback to run on the main thread.
     */
    fun submitTask(task: () -> Unit) {
        waitingTasks.add(task)
    }

    private fun init(config: WindowConfig) {
        println("Creating new window")
        // Configure GLFW
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE) // Focus window on open
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE) // For macOS compatibility

        // Create the window
        windowHandle = glfwCreateWindow(
            config.initialWindowWidth, config.initialWindowHeight, config.initialTitle, NULL, NULL
        )
        if (windowHandle == NULL) {
            glfwTerminate()
            throw RuntimeException("Failed to create the GLFW window")
        }
        glfwMakeContextCurrent(windowHandle)
        GL.createCapabilities()

        glfwSetWindowCloseCallback(windowHandle) {
            open = false
        }
    }

    fun show(config: WindowConfig = WindowConfig()) {
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        init(config)


        val dispatcher = GlfwCoroutineDispatcher()
        val compose = ComposeGlfwAdapter.create(
            config.initialWindowWidth, config.initialWindowHeight, dispatcher,
            density = Density(glfwGetWindowContentScale(windowHandle))
        )

        compose.setScene(config.initialWindowWidth, config.initialWindowHeight) {
            var color by remember { mutableStateOf(Color.Red) }
            LaunchedEffect(Unit) {
                delay(500)
                color = Color.Blue
            }
            Box(Modifier.background(color).fillMaxSize())
        }


        while (open) {
            glfwPollEvents()
            dispatcher.poll()
            compose.draw()
            GLFW.glfwSwapBuffers(windowHandle)
            waitingTasks.forEach { it() }
            waitingTasks.clear()

            Thread.sleep(30)// Temporary hack
        }
        close()
    }

    fun restart(config: WindowConfig = WindowConfig()) {
        close()
        init(config)
    }

    private fun close() {
        glfwDestroyWindow(windowHandle)
    }
}

private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    GLFW.glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}