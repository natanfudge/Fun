package natan.io.github.natanfudge.fu.window

import org.lwjgl.glfw.GLFW.GLFW_CLIENT_API
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_NO_API
import org.lwjgl.glfw.GLFW.GLFW_RESIZABLE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwWindowHint

class GlfwWebgpuWindow {
    fun show() {
//        glfwInit()
//        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
//        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
//        // Disable context creation, WGPU will manage that
//        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
//
//        val windowHandler: Long = glfwCreateWindow(width, height, title, NULL, NULL)
    }

    fun close() {
        println("new close")

    }
}