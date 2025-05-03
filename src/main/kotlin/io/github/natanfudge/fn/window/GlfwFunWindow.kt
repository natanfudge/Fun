package io.github.natanfudge.fn.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.webgpu.AutoCloseImpl
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryUtil.NULL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

var onTheFlyDebugRequested = false

data class GlfwConfig(
    val disableApi: Boolean,
    val showWindow: Boolean,
)

// Note we have this issue: https://github.com/gfx-rs/wgpu/issues/7663
class GlfwFunWindow(val glfw: GlfwConfig, val name: String) {
    private lateinit var instance: GlInitGlfwWindowInstance
     var open = false

    private var windowPos: IntOffset? = null

    private lateinit var callbacks: WindowCallbacks

    val handle get() = instance.windowHandle



    @OptIn(InternalComposeUiApi::class)
    private inner class GlInitGlfwWindowInstance(config: WindowConfig) {
        val waitingTasks = mutableListOf<() -> Unit>()

        /**
         * It's important to lock on this when modifying [waitingTasks] because [submitTask] occurs on a different thread than the running of [waitingTasks]
         */
        val taskLock = ReentrantLock()

        init {
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Initially invisible to give us time to move it to the correct place
//            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
            if (glfw.disableApi) {
                glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
            } else {
                glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API)
            }
            glfwWindowHint(GLFW_FLOATING, GLFW_TRUE) // Focus window on open
        }

        val windowHandle = glfwCreateWindow(
            config.initialWindowWidth, config.initialWindowHeight, config.initialTitle, NULL, NULL
        )

        init {
            // Unfloaty it because it's annoying, it was only enabled for it to be focused initially
            glfwSetWindowAttrib(windowHandle, GLFW_FLOATING, GLFW_FALSE)
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
                callbacks.windowMove(x,y)
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
                if (event.key == Key.P) onTheFlyDebugRequested = !onTheFlyDebugRequested
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
            if (!glfw.disableApi) {
                glfwMakeContextCurrent(windowHandle)
            }
            glfwDestroyWindow(windowHandle)
        }

    }
// 1534157416736
    // 1534157437760

    private var lastFrameTimeNano = 0L

    private val frameAutoclose = AutoCloseImpl()

    fun pollTasks() {
        with(instance) {
//                    println("Task lock for $name")
            taskLock.withLock {
                waitingTasks.forEach { it() }
                waitingTasks.clear()
            }
//                    println("Task unlock for $name")
        }
    }

    fun frame() {
        val time = System.nanoTime()
        val delta = time - lastFrameTimeNano
        lastFrameTimeNano = time
        frameAutoclose.use {
            with(callbacks) {
//                println("Before Window frame of $name")
//                if(glfw.disableApi) {
//                    glfwMakeContextCurrent(handle)
//                }
                it.frame(delta.toDouble() / 1e6)
//                println("After window frame of $name")
            }
        }

    }

    /**
     * Submits a callback to run on the main thread.
     */
    fun submitTask(task: () -> Unit) {
        instance.taskLock.withLock {
            instance.waitingTasks.add(task)
        }
    }


    fun show(config: WindowConfig, callbacks: WindowCallbacks, loop: Boolean = true) {
        open = true
        this.callbacks = callbacks
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        instance = GlInitGlfwWindowInstance(config)

        if(loop) {
            while (open) {
//            println("Polling in $name")
                glfwPollEvents()
//            println("After polling in $name")
                if (!open) break
                val time = System.nanoTime()
                val delta = time - lastFrameTimeNano
                if (delta >= 1e9 / config.maxFps) {
//                println("Frame passed in $name")

                    pollTasks()
//                println("Frame")
//                println("Before frame of $name")
                    frame()
//                println("After frame of $name" +
//                        "")
                }

            }

        }


    }

    fun restart(config: WindowConfig = WindowConfig()) {
        instance.close()
        instance = GlInitGlfwWindowInstance(config)
    }

    fun close() {
        open = false
        instance.close()
    }
}

private fun glfwGetCursorPos(window: Long): Offset {
    val x = DoubleArray(1)
    val y = DoubleArray(1)
    GLFW.glfwGetCursorPos(window, x, y)
    return Offset(x[0].toFloat(), y[0].toFloat())
}