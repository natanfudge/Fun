package io.github.natanfudge.fn.window

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