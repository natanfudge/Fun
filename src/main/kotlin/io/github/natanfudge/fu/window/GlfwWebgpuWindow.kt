package io.github.natanfudge.fu.window

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil.NULL

class GlfwWebgpuWindow {
    private var windowHandler: Long = 0
    private var open = true


    private val waitingTasks = mutableListOf<() -> Unit>()

    /**
     * Submits a callback to run on the main thread.
     */
    fun submitTask(task: () -> Unit) {
        waitingTasks.add(task)
    }

    private fun init() {
        println("Creating new window")
        // Configure GLFW
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE) // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE) // the window will be resizable
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE) // the window will be resizable

        // Create the window
        windowHandler = glfwCreateWindow(600, 600, "WebGPU Window", NULL, NULL)
        if (windowHandler == NULL) {
            glfwTerminate()
            throw RuntimeException("Failed to create the GLFW window")
        }

        glfwSetWindowCloseCallback(windowHandler) {
            open = false
        }
    }

    fun show() {
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        init()
        while (open) {
            glfwWaitEvents()
            waitingTasks.forEach { it() }
            waitingTasks.clear()
        }
        close()
    }

    fun restart() {
        close()
        init()
    }

    private fun close() {
        glfwDestroyWindow(windowHandler)
    }
}