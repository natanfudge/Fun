@file:OptIn(ExperimentalAtomicApi::class)

package io.github.natanfudge.fn.core.newstuff

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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



class NewGlfwWindow(val withOpenGL: Boolean, val showWindow: Boolean, val params: WindowConfig) : NewFun("GlfwWindow", params) {
    // Note we have this issue: https://github.com/gfx-rs/wgpu/issues/7663

    var handle by memo<WindowHandle> { null }

    var width = params.initialWindowWidth
    var height = params.initialWindowHeight

    val size get() = IntSize(width, height)

    override fun equals(other: Any?): Boolean {
        return other is NewGlfwWindow && other.handle == this.handle
    }

    override fun hashCode(): Int {
        return handle.hashCode()
    }

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

        glfwSetWindowCloseCallback(handle) {
            events.input(InputEvent.WindowClosePressed)
        }
        glfwSetWindowPosCallback(handle) { _, x, y ->
            events.input(InputEvent.WindowMove(IntOffset(x, y)))
        }

        glfwSetWindowSizeCallback(handle) { _, windowWidth, windowHeight ->
            minimized = windowWidth == 0 || windowHeight == 0

            events.windowResized(IntSize(windowWidth, windowHeight))
        }



        glfwSetMouseButtonCallback(handle) { _, button, action, mods ->
            events.input(
                InputEvent.PointerEvent(
                    position = glfwGetCursorPos(handle
                    ),
                    eventType = when (action) {
                        GLFW_PRESS -> PointerEventType.Press
                        GLFW_RELEASE -> PointerEventType.Release
                        else -> PointerEventType.Unknown
                    },
                    nativeEvent = AwtMouseEvent(getAwtMods(handle)),
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

        glfwSetCursorPosCallback(handle) { _, xpos, ypos ->
            val position = Offset(xpos.toFloat(), ypos.toFloat())
            events.input(
                InputEvent.PointerEvent(
                    position = position,
                    eventType = PointerEventType.Move,
                    nativeEvent = AwtMouseEvent(getAwtMods(handle))
                )
            )
        }

        glfwSetScrollCallback(handle) { _, xoffset, yoffset ->
            events.input(
                InputEvent.PointerEvent(
                    eventType = PointerEventType.Scroll,
                    position = glfwGetCursorPos(handle),
                    scrollDelta = Offset(xoffset.toFloat(), -yoffset.toFloat()),
                    nativeEvent = AwtMouseWheelEvent(getAwtMods(handle))
                )
            )
        }

        glfwSetCursorEnterCallback(handle) { _, entered ->
            events.input(
                InputEvent.PointerEvent(
                    position = glfwGetCursorPos(handle),
                    eventType = if (entered) PointerEventType.Enter else PointerEventType.Exit,
                    nativeEvent = AwtMouseEvent(getAwtMods(handle))
                )
            )
        }

        glfwSetKeyCallback(handle) { _, key, scancode, action, mods ->
            val event = glfwToComposeEvent(key, action, mods)
            if (event.key == Key.P) onTheFlyDebugRequested = !onTheFlyDebugRequested
            events.input(InputEvent.KeyEvent(event))
        }

        glfwSetCharCallback(handle) { _, codepoint ->
            for (char in Character.toChars(codepoint)) {
                events.input(
                    InputEvent.KeyEvent(typedCharacterToComposeEvent(char))
                )
            }
        }

        glfwSetWindowContentScaleCallback(handle) { _, xscale, _ ->
            events.densityChange(Density(xscale))
        }

        events.beforeFrame.listen {
            glfwPollEvents()
        }
        events.windowResized.listen { (width, height) ->
            this.width = width
            this.height = height
        }
    }



    override fun toString(): String {
        return "GLFW Window $handle"
    }

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
                } else {
                    glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
                    if (glfwRawMouseMotionSupported()) {
                        glfwSetInputMode(handle, GLFW_RAW_MOUSE_MOTION, GLFW_FALSE);
                    }
                }
            }
        }

    override fun cleanup() {
        glfwSetWindowCloseCallback(handle, null)
        glfwDestroyWindow(handle)
    }
}



private fun glfwGetCursorPos(window: Long): Offset {
    val x = DoubleArray(1)
    val y = DoubleArray(1)
    glfwGetCursorPos(window, x, y)
    return Offset(x[0].toFloat(), y[0].toFloat())
}