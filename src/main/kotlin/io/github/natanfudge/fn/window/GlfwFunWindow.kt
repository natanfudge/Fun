package io.github.natanfudge.fn.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.webgpu.AutoClose
import io.github.natanfudge.fn.webgpu.AutoCloseImpl
import org.jetbrains.skiko.currentNanoTime
import org.lwjgl.glfw.GLFW
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

interface RepeatingWindowCallbacks {
    fun AutoClose.frame(delta: Double) {}

    /**
     * Will be called once on startup as well
     */
    fun resize(width: Int, height: Int) {}

    /**
     * You should close the window here
     */
    fun windowClosePressed() {}
    @Deprecated("I don't think i need this one")
    fun setMinimized(minimized: Boolean) {}
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
    ) {
    }

    fun keyEvent(
        event: KeyEvent,
    ) {
    }

    fun densityChange(newDensity: Density) {}
}

interface WindowCallbacks : RepeatingWindowCallbacks {
    fun init(handle: WindowHandle) {}

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
//            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
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
                callbacks.windowClosePressed()
//                close()
            }
            glfwSetWindowIconifyCallback(windowHandle) { _, minimized ->
                callbacks.setMinimized(minimized)
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
                callbacks.resize(windowWidth, windowHeight)
                frame() // We want to content to adapt faster to resize changes so we rerender right away.
            }

            glfwSetMouseButtonCallback(windowHandle) { _, button, action, mods ->
                // We're going to send the clicks to the GUI even if it is not focused, to be able to actually move from opengl focus to Compose focus.
                callbacks.pointerEvent(
                    position = glfwGetCursorPos(windowHandle),
                    eventType = when (action) {
                        GLFW_PRESS -> PointerEventType.Press
                        GLFW_RELEASE -> PointerEventType.Release
                        else -> PointerEventType.Unknown
                    },
                    nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))
                )
            }

            glfwSetCursorPosCallback(windowHandle) { window, xpos, ypos ->
                callbacks.pointerEvent(
                    position = Offset(xpos.toFloat(), ypos.toFloat()),
                    eventType = PointerEventType.Move,
                    nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))
                )
            }

            glfwSetScrollCallback(windowHandle) { window, xoffset, yoffset ->
                callbacks.pointerEvent(
                    eventType = PointerEventType.Scroll,
                    position = glfwGetCursorPos(windowHandle),
                    scrollDelta = Offset(xoffset.toFloat(), -yoffset.toFloat()),
                    nativeEvent = AwtMouseWheelEvent(getAwtMods(windowHandle))
                )

            }

            glfwSetCursorEnterCallback(windowHandle) { _, entered ->
                callbacks.pointerEvent(
                    position = glfwGetCursorPos(windowHandle),
                    eventType = if (entered) PointerEventType.Enter else PointerEventType.Exit,
                    nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))
                )
            }

            glfwSetKeyCallback(windowHandle) { _, key, scancode, action, mods ->
                val event = glfwToComposeEvent(key, action, mods)
                callbacks.keyEvent(event)
            }

            glfwSetCharCallback(windowHandle) { window, codepoint ->
                for (char in Character.toChars(codepoint)) {
                    callbacks.keyEvent(typedCharacterToComposeEvent(char))
                }
            }

            glfwSetWindowContentScaleCallback(windowHandle) { _, xscale, _ ->
                callbacks.densityChange(Density(xscale))
            }

            callbacks.init(windowHandle)
            callbacks.resize(config.initialWindowWidth, config.initialWindowHeight)
        }


        fun close() {
            glfwSetWindowCloseCallback(windowHandle, null)
            glfwDestroyWindow(windowHandle)
        }

    }


    private var lastFrameTimeNano = 0L

    private val frameAutoclose = AutoCloseImpl()

    private fun frame() {
        val time = System.nanoTime()
        val delta = time - lastFrameTimeNano
        lastFrameTimeNano = time
        frameAutoclose.use {
            with(callbacks) {
                it.frame(delta.toDouble() / 1e9)
            }
        }

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
            if (!open) break
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

private fun glfwGetCursorPos(window: Long): Offset {
    val x = DoubleArray(1)
    val y = DoubleArray(1)
    GLFW.glfwGetCursorPos(window, x, y)
    return Offset(x[0].toFloat(), y[0].toFloat())
}