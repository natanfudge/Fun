@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.util.FunLogLevel
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.webgpu.AutoCloseImpl
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryUtil.NULL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.withLock

var onTheFlyDebugRequested = false

data class GlfwConfig(
    val disableApi: Boolean,
    val showWindow: Boolean,
)

data class WindowDimensions(
    val width: Int,
    val height: Int,
    val handle: WindowHandle,
)

class GlfwWindow(val handle: WindowHandle, val glfw: GlfwConfig, val init: WindowConfig) : AutoCloseable {
    private val waitingTasks = mutableListOf<() -> Unit>()

    /**
     * It's important to lock on this when modifying [waitingTasks] because [submitTask] occurs on a different thread than the running of [waitingTasks]
     */
    private val taskLock = ReentrantLock()

    var minimized = false

    fun pollTasks() {
        taskLock.withLock {
            waitingTasks.forEach { it() }
            waitingTasks.clear()
        }
    }

    /**
     * Submits a callback to run on the main thread.
     */
    fun submitTask(task: () -> Unit) {
        taskLock.withLock {
            waitingTasks.add(task)
        }
    }


    override fun close() {
        glfwSetWindowCloseCallback(handle, null)
        if (!glfw.disableApi) {
            glfwMakeContextCurrent(handle)
        }
        glfwDestroyWindow(handle)
    }
}


// Note we have this issue: https://github.com/gfx-rs/wgpu/issues/7663
class GlfwWindowConfig(val glfw: GlfwConfig, val name: String, val config: WindowConfig) {
    fun submitTask(task: () -> Unit) = window.submitTask(task)

    val windowLifecycle: Lifecycle<Unit, GlfwWindow> = ProcessLifecycle.bind("GLFW $name Window") {
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Initially invisible to give us time to move it to the correct place
        if (glfw.disableApi) {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        } else {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API)
        }
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE) // Focus window on open

        val windowHandle = glfwCreateWindow(
            config.initialWindowWidth, config.initialWindowHeight, config.initialTitle, NULL, NULL
        )
        val window = GlfwWindow(windowHandle, glfw, config)

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
            open = false
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
            callbacks.windowMove(x, y)
        }

        glfwSetWindowSizeCallback(windowHandle) { _, windowWidth, windowHeight ->
            if (windowWidth != 0 && windowHeight != 0) {
                window.minimized = false
                dimensionsLifecycle.restart()
                callbacks.resize(windowWidth, windowHeight)
                frame() // We want to content to adapt faster to resize changes so we rerender right away.
            } else {
                window.minimized = true
            }
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

//        callbacks.init(windowHandle)
        callbacks.resize(config.initialWindowWidth, config.initialWindowHeight)
        window
    }
    val dimensionsLifecycle = windowLifecycle.bind("GLFW fixed size window") {
        val w = IntArray(1)
        val h = IntArray(1)
        glfwGetWindowSize(it.handle, w, h)
        WindowDimensions(w[0], h[0], it.handle)
    }
    val frameLifecycle = Lifecycle.create<Double, Double>("GLFW Frame", FunLogLevel.Verbose) {
        it
    }

    internal var open = true

    private var windowPos: IntOffset? = null

    private var callbacks: RepeatingWindowCallbacks = object : RepeatingWindowCallbacks {}

    internal val window: GlfwWindow by windowLifecycle

    val handle get() = window.handle

    internal var lastFrameTimeNano = 0L

    private val frameAutoclose = AutoCloseImpl()

    fun frame() {
        val time = System.nanoTime()
        val delta = time - lastFrameTimeNano
        lastFrameTimeNano = time
        frameAutoclose.use {
            val deltaMs = delta.toDouble() / 1e6


            frameLifecycle.start(deltaMs)
            with(callbacks) {
                it.frame(deltaMs)
            }
            //TODO: we can do away with WindowCallbacks#init, resize, frame soon, getting rid of frame might be hard because we need present() to run AFTER. in that case we can just put it in the code I think. or on the close of the frame on the WEBGPU parent!
            frameLifecycle.end()
        }
    }

    fun setCallbacks(callbacks: RepeatingWindowCallbacks) {
        this.callbacks = callbacks
    }

    //TODO: current problem: open is set to false when the window is closed. i could go back to the previous approach of closing manually, but i do want to figure out
    // a lifecycle way of doing it.

//    fun show(config: WindowConfig, loop: Boolean = true) {
////        GLFWErrorCallback.createPrint(System.err).set()
////
////        // Initialize GLFW. Most GLFW functions will not work before doing this.
////        if (!glfwInit()) {
////            throw IllegalStateException("Unable to initialize GLFW")
////        }
//
//
////        windowLifecycle.start(Unit)
//
////        if (loop) {
////            while (open) {
////                glfwPollEvents()
////                if (!open) break
////                if (window.minimized) continue
////                val time = System.nanoTime()
////                val delta = time - lastFrameTimeNano
////                if (delta >= 1e9 / config.maxFps) {
////                    window.pollTasks()
////                    frame()
////                }
////            }
////        }
//
//    }

    fun restart() {
        windowLifecycle.restart()
    }

    fun close() {
        open = false
        windowLifecycle.end()
    }
}

class GlfwGameLoop(var window: GlfwWindowConfig) {
//    val lock = Semaphore(1)

    var locked = false

    var reloadCallback: (() -> Unit)? = null

    fun loop() {
        while (window.open) {
            checkForReloads()
            glfwPollEvents()
            if (!window.open) break
            if (window.window.minimized) continue
            val time = System.nanoTime()
            val delta = time - window.lastFrameTimeNano
            if (delta >= 1e9 / window.config.maxFps) {
                checkForReloads()
                window.window.pollTasks()
                checkForReloads()
//                if (lock.availablePermits() == 0) {
//                    println("Acquiring permit")
//                    lock.acquire()
//                    // Check again for tasks before next frame
//                    window.window.pollTasks()
//                }
                window.frame()
            }
        }
    }

    private fun checkForReloads() {
        while (locked) {
            if (reloadCallback != null) {
                reloadCallback!!()
                reloadCallback = null
                locked = false
            }
            Thread.sleep(10)
        }
    }
}

private fun glfwGetCursorPos(window: Long): Offset {
    val x = DoubleArray(1)
    val y = DoubleArray(1)
    glfwGetCursorPos(window, x, y)
    return Offset(x[0].toFloat(), y[0].toFloat())
}