@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.core.mustRerun
import io.github.natanfudge.fn.util.FunLogLevel
import io.github.natanfudge.fn.util.Lifecycle
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryUtil.NULL
import java.util.concurrent.locks.ReentrantLock
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
    val window: GlfwWindow,
)

class GlfwWindow(val handle: WindowHandle, val glfw: GlfwConfig, val init: WindowConfig) : AutoCloseable {
    override fun toString(): String {
        return "GLFW Window $handle"
    }

    private val waitingTasks = mutableListOf<() -> Unit>()
    val callbacks = mutableMapOf<String, WindowCallbacks>()

    var lastFrameTimeNano = System.nanoTime()

    /**
     * It's important to lock on this when modifying [waitingTasks] because [submitTask] occurs on a different thread than the running of [waitingTasks]
     */
    private val taskLock = ReentrantLock()

    var minimized = false

    var open = true

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

class GlfwFrame(
    val window: GlfwWindow,
) {
    val time = System.nanoTime()
    val deltaMs = (time - window.lastFrameTimeNano).toDouble() / 1e6

    init {
        window.lastFrameTimeNano = time
    }

    override fun toString(): String {
        return "Frame delta=$deltaMs, window=$window"
    }
}


// Note we have this issue: https://github.com/gfx-rs/wgpu/issues/7663
class GlfwWindowConfig(val glfw: GlfwConfig, val name: String, val config: WindowConfig) {
    fun submitTask(task: () -> Unit) = windowLifecycle.value?.submitTask(task)

    private fun dimensionsLifecycleLabel() = "GLFW Dimensions ($name)"

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



        if (glfw.showWindow) {
            glfwShowWindow(windowHandle)
        }

        window
    }

    init {
        windowLifecycle.bind("GLFW Callbacks ($name)") { window ->
            val windowHandle = window.handle
            val callbacks = window.callbacks.values
            glfwSetWindowCloseCallback(windowHandle) {
                callbacks.forEach { it.windowClosePressed() }
//                callbacks.windowClosePressed()
                window.open = false
            }
            glfwSetWindowPosCallback(windowHandle) { _, x, y ->
                callbacks.forEach { it.windowMove(x,y) }
            }

            glfwSetWindowSizeCallback(windowHandle) { _, windowWidth, windowHeight ->
                if (windowWidth != 0 && windowHeight != 0) {
                    window.minimized = false

                    dimensionsLifecycle.restart()

//                    // Since this callback doesn't always get recalled on hot reload, we need to make sure we access the latest lifecycle.
//                    // This is kind of a hack fix.
//                    ProcessLifecycle.restartByLabel(dimensionsLifecycleLabel())
                } else {
                    window.minimized = true
                }
            }



            glfwSetMouseButtonCallback(windowHandle) { _, button, action, mods ->
                callbacks.forEach {
                    it.pointerEvent(
                        position = glfwGetCursorPos(windowHandle),
                        eventType = when (action) {
                            GLFW_PRESS -> PointerEventType.Press
                            GLFW_RELEASE -> PointerEventType.Release
                            else -> PointerEventType.Unknown
                        },
                        nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))
                    )
                }
            }

            glfwSetCursorPosCallback(windowHandle) { window, xpos, ypos ->
                callbacks.forEach {
                    it.pointerEvent(
                        position = Offset(xpos.toFloat(), ypos.toFloat()),
                        eventType = PointerEventType.Move,
                        nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))
                    )
                }
            }

            glfwSetScrollCallback(windowHandle) { window, xoffset, yoffset ->
                callbacks.forEach {
                    it.pointerEvent(
                        eventType = PointerEventType.Scroll,
                        position = glfwGetCursorPos(windowHandle),
                        scrollDelta = Offset(xoffset.toFloat(), -yoffset.toFloat()),
                        nativeEvent = AwtMouseWheelEvent(getAwtMods(windowHandle))
                    )
                }

            }

            glfwSetCursorEnterCallback(windowHandle) { _, entered ->
                callbacks.forEach {
                    it.pointerEvent(
                        position = glfwGetCursorPos(windowHandle),
                        eventType = if (entered) PointerEventType.Enter else PointerEventType.Exit,
                        nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))
                    )
                }
            }

            glfwSetKeyCallback(windowHandle) { _, key, scancode, action, mods ->
                val event = glfwToComposeEvent(key, action, mods)
                if (event.key == Key.P) onTheFlyDebugRequested = !onTheFlyDebugRequested
                callbacks.forEach { it.keyEvent(event) }
            }

            glfwSetCharCallback(windowHandle) { window, codepoint ->
                for (char in Character.toChars(codepoint)) {
                    callbacks.forEach { it.keyEvent(typedCharacterToComposeEvent(char)) }
                }
            }

            glfwSetWindowContentScaleCallback(windowHandle) { _, xscale, _ ->
                callbacks.forEach { it.densityChange(Density(xscale)) }
            }
            Unit
        }.mustRerun() // Make sure callbacks are not stale
    }


    val dimensionsLifecycle: Lifecycle<GlfwWindow, WindowDimensions> = windowLifecycle.bind("GLFW Dimensions ($name)") {
        val w = IntArray(1)
        val h = IntArray(1)
        glfwGetWindowSize(it.handle, w, h)
        WindowDimensions(w[0], h[0], it)
    }

    val frameLifecycle = dimensionsLifecycle.bind("GLFW Frame of $name", FunLogLevel.Verbose) {
        GlfwFrame(it.window)
    }

    val eventPollLifecycle = Lifecycle.create<Unit, Unit>("GLFW Event poll", FunLogLevel.Verbose) {
        glfwPollEvents()
    }



//    private var callbacks: WindowCallbacks = object : WindowCallbacks {}

//    fun setCallbacks(callbacks: WindowCallbacks) {
//        this.callbacks = callbacks
//    }


    fun close() {
        windowLifecycle.value?.open = false
        windowLifecycle.end()
    }
}


/**
 * The [window] needs to be updated whenever it is recreated.
 */
class GlfwGameLoop(var window: GlfwWindowConfig) {

    var locked = false

    var reloadCallback: (() -> Unit)? = null

    private val currentWindow get() = window.windowLifecycle.assertValue

    fun loop() {
        while (currentWindow.open) {
            checkForReloads()
            if (!window.eventPollLifecycle.isInitialized) window.eventPollLifecycle.start(Unit)
            else window.eventPollLifecycle.restart()
            checkForReloads()
            if (!currentWindow.open) break
            if (currentWindow.minimized) continue
            val time = System.nanoTime()
            val delta = time - currentWindow.lastFrameTimeNano
            if (delta >= 1e9 / currentWindow.init.maxFps) {
                checkForReloads()
                currentWindow.pollTasks()
                checkForReloads()
                window.frameLifecycle.restart()
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