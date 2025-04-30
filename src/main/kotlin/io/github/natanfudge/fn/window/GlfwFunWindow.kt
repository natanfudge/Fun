package io.github.natanfudge.fn.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import org.jetbrains.skiko.currentNanoTime
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryUtil.NULL


data class WindowConfig(
    val initialWindowWidth: Int = 800,
    val initialWindowHeight: Int = 600,
    val initialTitle: String = "Fun",
    val fps: Int = 60,
)

interface WindowCallbacks {
    fun init(handle: WindowHandle)
    fun frame(delta: Double)
    fun resize(width: Int, height: Int)
    fun pointerEvent(
        eventType: PointerEventType,
        position: Offset,
        scrollDelta: Offset = Offset.Zero,
        timeMillis: Long = currentTimeForEvent(),
        type: PointerType = PointerType.Mouse,
        buttons: PointerButtons? = null,
        keyboardModifiers: PointerKeyboardModifiers? = null,
        nativeEvent: Any? = null,
        button: PointerButton? = null,
    )

    fun keyEvent(
        event: KeyEvent,
    )

    fun densityChange(newDensity: Density)
}

private fun currentTimeForEvent(): Long = (currentNanoTime() / 1E6).toLong()


typealias WindowHandle = Long

data class GlfwConfig(
    val disableApi: Boolean,
    val showWindow: Boolean,
)

class GlfwFunWindow(val glfw: GlfwConfig) {
    private lateinit var instance: GlInitGlfwWindowInstance
    private var open = true

    private var windowPos: IntOffset? = null

    private lateinit var callbacks: WindowCallbacks

    @OptIn(InternalComposeUiApi::class)
    private inner class GlInitGlfwWindowInstance(config: WindowConfig) {
        val waitingTasks = mutableListOf<() -> Unit>()

        init {
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Initially invisible to give us time to move it to the correct place
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
            if (glfw.disableApi) glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
            glfwWindowHint(GLFW_FLOATING, GLFW_TRUE) // Focus window on open
        }

        val windowHandle = glfwCreateWindow(
            config.initialWindowWidth, config.initialWindowHeight, config.initialTitle, NULL, NULL
        )

        init {
            if (windowHandle == NULL) {
                glfwTerminate()
                throw RuntimeException("Failed to create the GLFW window")
            }
            if (!glfw.disableApi) {
                glfwMakeContextCurrent(windowHandle)
                GL.createCapabilities()
            }

            glfwSetWindowCloseCallback(windowHandle) {
                close()
            }

            if (windowPos != null) {
                // Keep the window in the same position when reloading
                glfwSetWindowPos(windowHandle, windowPos!!.x, windowPos!!.y)
            }
            if (glfw.showWindow) {
                glfwShowWindow(windowHandle)
            }
            glfwSetWindowPosCallback(windowHandle) { _, x, y ->
                windowPos = IntOffset(x, y)
            }

            glfwSetWindowSizeCallback(windowHandle) { _, windowWidth, windowHeight ->
                frame() // We want to content to adapt faster to resize changes so we rerender right away.
            }
            callbacks.init(windowHandle)
        }


        fun close() {
            glfwSetWindowCloseCallback(windowHandle, null)
            glfwDestroyWindow(windowHandle)
        }

    }


    private var lastFrameTimeNano = 0L

    private fun frame() {
        val time = System.nanoTime()
        val delta = time - lastFrameTimeNano
        lastFrameTimeNano = time
        callbacks.frame(delta.toDouble() / 1e9)
    }

    /**
     * Submits a callback to run on the main thread.
     */
    fun submitTask(task: () -> Unit) {
        instance.waitingTasks.add(task)
    }


    fun show(config: WindowConfig, callbacks: WindowCallbacks) {
        this.callbacks = callbacks
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        instance = GlInitGlfwWindowInstance(config)

        while (open) {
            glfwPollEvents()
            val time = System.nanoTime()
            val delta = time - lastFrameTimeNano
            if (delta >= 1e9 / config.fps) {
                frame()
            }
            with(instance) {
                waitingTasks.forEach { it() }
                waitingTasks.clear()
            }
        }
        instance.close()
    }

    fun restart(config: WindowConfig = WindowConfig()) {
        instance.close()
        instance = GlInitGlfwWindowInstance(config)
    }

    fun close() {
        open = false
    }
}