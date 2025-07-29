package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.window.GlfwWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWErrorCallback


object GlfwWindowProvider {
    fun initialize() {
        println("Halo")
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }
    }

}






