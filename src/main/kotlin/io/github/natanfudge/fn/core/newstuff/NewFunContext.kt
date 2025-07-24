package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.window.GlfwWindow
import io.github.natanfudge.fn.window.WindowConfig
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFWErrorCallback

class NewFunContext(val appCallback: () -> Unit) {
    init {
        NewFunContextRegistry.setContext(this)
    }
    val cache = FunCache()

    fun start() {
        appCallback()
    }
}




internal object NewFunContextRegistry {
    private lateinit var context: NewFunContext
    fun setContext(context: NewFunContext) {
        this.context = context
    }

    fun getContext() = context
}

class FunBaseApp(window: WindowConfig): NewFun("FunBaseApp") {
    val glfwConfig by memo {
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }
    }
    val window = GlfwWindow(withOpenGL = false, showWindow = true, window)

}

fun main() {
    val context = NewFunContext {
        val base = FunBaseApp(WindowConfig())
    }

    context.start()

    while (true) {
        Thread.sleep(100)
    }

}