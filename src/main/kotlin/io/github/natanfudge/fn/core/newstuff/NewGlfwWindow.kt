@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.core.newstuff

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.core.FunLogLevel
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.window.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryUtil.NULL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.withLock
import kotlin.properties.Delegates

var onTheFlyDebugRequested = false

data class GlfwConfig(
    val disableApi: Boolean,
    val showWindow: Boolean,
)

interface WindowDimensions {
    val width: Int
    val height: Int
}


data class GlfwWindowDimensions(
    override val width: Int,
    override val height: Int,
    val window: NewGlfwWindow,
) : WindowDimensions

class NewGlfwWindow(val withOpenGL: Boolean, val showWindow: Boolean, val params: WindowConfig) : NewFun("GlfwWindow") {

    override val keys = mutableListOf(params)

    //TODO: emit events into context

    var handle by funValue<WindowHandle>(null)
    override fun init() {
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Initially invisible to give us time to move it to the correct place
        if (withOpenGL) {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API)
        } else {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        }
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE) // Focus window on open

        handle = glfwCreateWindow(
            params.initialWindowWidth, params.initialWindowHeight, params.initialTitle, NULL, NULL
        )

        if (handle == NULL) {
            glfwTerminate()
            throw RuntimeException("Failed to create the GLFW window")
        }
        // Unfloaty it because it's annoying, it was only enabled for it to be focused initially
        glfwSetWindowAttrib(handle, GLFW_FLOATING, GLFW_FALSE)

        if (showWindow) {
            glfwShowWindow(handle)
        }
    }

    override fun toString(): String {
        return "GLFW Window $handle"
    }

    val inputEvent = EventEmitter<InputEvent>()
    val densityChangeEvent = EventEmitter<Density>()

    var lastFrameTimeNano = System.nanoTime()


    /**
     * If cursor is not locked, will return the position of the cursor.
     */

    var minimized = false
    var cursorLocked = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    if (glfwRawMouseMotionSupported()) {
                        glfwSetInputMode(handle, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
                    }
//                    cursorPos = null
                } else {
                    glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
                    if (glfwRawMouseMotionSupported()) {
                        glfwSetInputMode(handle, GLFW_RAW_MOUSE_MOTION, GLFW_FALSE);
                    }
                }
            }
        }

    var open = true



    override fun cleanup() {
        glfwSetWindowCloseCallback(handle, null)
        glfwDestroyWindow(handle)
    }
}

class GlfwFrame(
    val window: NewGlfwWindow,
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
class GlfwWindowConfig(val glfw: GlfwConfig, val name: String, val windowParameters: WindowConfig, parentLifecycle: Lifecycle<Unit> = ProcessLifecycle) {

    val windowLifecycle: Lifecycle<NewGlfwWindow> = parentLifecycle.bind("GLFW $name Window") {
        NewGlfwWindow(!glfw.disableApi, glfw.showWindow, windowParameters)
    }

    init {
        windowLifecycle.bind("GLFW Callbacks ($name)") { window ->
            val windowHandle = window.handle
            glfwSetWindowCloseCallback(windowHandle) {
                window.inputEvent.emit(InputEvent.WindowClosePressed)
                window.open = false
            }
            glfwSetWindowPosCallback(windowHandle) { _, x, y ->
                window.inputEvent.emit(InputEvent.WindowMove(IntOffset(x, y)))
            }

            glfwSetWindowSizeCallback(windowHandle) { _, windowWidth, windowHeight ->
                if (windowWidth != 0 && windowHeight != 0) {
                    window.minimized = false

                    dimensionsLifecycle.restart()
                } else {
                    window.minimized = true
                }
            }



            glfwSetMouseButtonCallback(windowHandle) { _, button, action, mods ->
                window.inputEvent.emit(
                    InputEvent.PointerEvent(
                        position = glfwGetCursorPos(windowHandle),
                        eventType = when (action) {
                            GLFW_PRESS -> PointerEventType.Press
                            GLFW_RELEASE -> PointerEventType.Release
                            else -> PointerEventType.Unknown
                        },
                        nativeEvent = AwtMouseEvent(getAwtMods(windowHandle)),
                        button = when (button) {
                            GLFW_MOUSE_BUTTON_LEFT -> PointerButton.Primary
                            GLFW_MOUSE_BUTTON_RIGHT -> PointerButton.Secondary
                            GLFW_MOUSE_BUTTON_MIDDLE -> PointerButton.Tertiary
                            GLFW_MOUSE_BUTTON_4 -> PointerButton.Back
                            GLFW_MOUSE_BUTTON_5 -> PointerButton.Forward
                            else -> PointerButton.Forward // Default to Forward on unknown button
                        }
                    )
                )
            }

            glfwSetCursorPosCallback(windowHandle) { _, xpos, ypos ->
                val position = Offset(xpos.toFloat(), ypos.toFloat())
                window.inputEvent.emit(
                    InputEvent.PointerEvent(
                        position = position,
                        eventType = PointerEventType.Move,
                        nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))

                    )
                )
            }

            glfwSetScrollCallback(windowHandle) { _, xoffset, yoffset ->
                window.inputEvent.emit(
                    InputEvent.PointerEvent(
                        eventType = PointerEventType.Scroll,
                        position = glfwGetCursorPos(windowHandle),
                        scrollDelta = Offset(xoffset.toFloat(), -yoffset.toFloat()),
                        nativeEvent = AwtMouseWheelEvent(getAwtMods(windowHandle))
                    )
                )


            }

            glfwSetCursorEnterCallback(windowHandle) { _, entered ->
                window.inputEvent.emit(
                    InputEvent.PointerEvent(
                        position = glfwGetCursorPos(windowHandle),
                        eventType = if (entered) PointerEventType.Enter else PointerEventType.Exit,
                        nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))
                    )
                )
            }

            glfwSetKeyCallback(windowHandle) { _, key, scancode, action, mods ->
                val event = glfwToComposeEvent(key, action, mods)
                if (event.key == Key.P) onTheFlyDebugRequested = !onTheFlyDebugRequested
                window.inputEvent.emit(InputEvent.KeyEvent(event))
            }

            glfwSetCharCallback(windowHandle) { _, codepoint ->
                for (char in Character.toChars(codepoint)) {
                    window.inputEvent.emit(
                        InputEvent.KeyEvent(typedCharacterToComposeEvent(char))
                    )
                }
            }

            glfwSetWindowContentScaleCallback(windowHandle) { _, xscale, _ ->
                window.densityChangeEvent.emit(Density(xscale))
            }
            Unit
        }
    }


    val dimensionsLifecycle: Lifecycle<GlfwWindowDimensions> = windowLifecycle.bind("GLFW Dimensions ($name)") {
        val w = IntArray(1)
        val h = IntArray(1)
        glfwGetWindowSize(it.handle, w, h)
        GlfwWindowDimensions(w[0], h[0], it)
    }

    val frameLifecycle = dimensionsLifecycle.bind("GLFW Frame of $name", FunLogLevel.Verbose) {
        GlfwFrame(it.window)
    }

    val HotReloadSave = parentLifecycle.bind("GLFW Reload Save of $name") {
        HotReloadRestarter()
    }

    val eventPollLifecycle = parentLifecycle.bind("GLFW Event poll of $name", FunLogLevel.Verbose) {
        glfwPollEvents()
    }


    fun close() {
        windowLifecycle.value?.open = false
        windowLifecycle.end()
    }
}


class HotReloadRestarter : AutoCloseable {
    var restarted = false
    val handle = FunHotReload.reloadEnded.listenUnscoped {
        restarted = true
    }

    override fun close() {
        handle.close()
    }
}


private fun glfwGetCursorPos(window: Long): Offset {
    val x = DoubleArray(1)
    val y = DoubleArray(1)
    glfwGetCursorPos(window, x, y)
    return Offset(x[0].toFloat(), y[0].toFloat())
}