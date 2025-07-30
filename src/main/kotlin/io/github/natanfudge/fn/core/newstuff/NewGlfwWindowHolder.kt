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
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.window.*
import org.jetbrains.compose.reload.agent.sendAsync
import org.jetbrains.compose.reload.core.WindowId
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryUtil.NULL
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

class GlfwWindowEffect(val withOpenGL: Boolean, val showWindow: Boolean, val params: WindowConfig, val events: NewFunEvents) : InvalidationKey() {
    init {
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Initially invisible to give us time to move it to the correct place
        if (withOpenGL) {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API)
        } else {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        }
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE) // Focus window on open
    }


    val handle = glfwCreateWindow(
        params.initialWindowWidth, params.initialWindowHeight, params.initialTitle, NULL, NULL
    )


    init {
        if (handle == NULL) {
            glfwTerminate()
            throw RuntimeException("Failed to create the GLFW window")
        }
        // Unfloaty it because it's annoying, it was only enabled for it to be focused initially
        glfwSetWindowAttrib(handle, GLFW_FLOATING, GLFW_FALSE)

        if (showWindow) {
            glfwShowWindow(handle)
        }

        notifyCHROfWindowPosition()


        glfwSetWindowCloseCallback(handle) {
            events.input(InputEvent.WindowClosePressed)
        }
        glfwSetWindowPosCallback(handle) { _, x, y ->
            events.input(InputEvent.WindowMove(IntOffset(x, y)))
        }

        glfwSetWindowSizeCallback(handle) { _, windowWidth, windowHeight ->
            val size = IntSize(windowWidth, windowHeight)
            events.windowResized(size)
            events.afterWindowResized(size)

        }



        glfwSetMouseButtonCallback(handle) { _, button, action, mods ->
            events.input(
                InputEvent.PointerEvent(
                    position = glfwGetCursorPos(
                        handle
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

        println("Foo")
    }


    override fun equals(other: Any?): Boolean {
        return other is NewGlfwWindowHolder && other.handle == this.handle
    }

    override fun hashCode(): Int {
        return handle.hashCode()
    }

    private fun notifyCHROfWindowPosition() {
        val x = IntArray(1)
        val y = IntArray(1)
        val w = IntArray(1)
        val h = IntArray(1)

        glfwGetWindowPos(handle, x, y)
        glfwGetWindowSize(handle, w, h)
        OrchestrationMessage.ApplicationWindowPositioned(WindowId(handle.toString()), x[0], y[0], w[0], h[0], false)
            .sendAsync()
    }


    override fun toString(): String {
        return "GLFW Window $handle"
    }


    override fun close() {
        OrchestrationMessage.ApplicationWindowGone(WindowId(handle.toString()))
            .sendAsync()
        glfwSetWindowCloseCallback(handle, null)
        glfwDestroyWindow(handle)
    }
}




class NewGlfwWindowHolder(val withOpenGL: Boolean, val showWindow: Boolean, val params: WindowConfig) : NewFun("GlfwWindow") {
    // Note we have this issue: https://github.com/gfx-rs/wgpu/issues/7663

    val effect by cached(InvalidationKey.None) {
        GlfwWindowEffect(withOpenGL, showWindow, params, events)
    }

    var width by memo { params.initialWindowWidth }
    var height by memo { params.initialWindowHeight }
    var windowPos by memo { IntOffset(0, 0) }
    val size get() = IntSize(width, height)

    val minimized get() = size.isEmpty


    val handle get() = effect.handle

    init {
        events.beforeFrame.listen {
            glfwPollEvents()
        }
        events.windowResized.listen { (width, height) ->
            val previouslyMinimized = this.size.isEmpty
            if (!previouslyMinimized && (width == 0 || height == 0)) {
                OrchestrationMessage.ApplicationWindowGone(WindowId(handle.toString()))
                    .sendAsync()
            }
            if (previouslyMinimized && width != 0 && height != 0) {
                notifyCHROfWindowPosition()
            }
            this.width = width
            this.height = height
        }
        events.input.listen {
            if (it is InputEvent.WindowMove) {
                this.windowPos = it.offset
                notifyCHROfWindowPosition()
            }
        }
    }

    private fun notifyCHROfWindowPosition() {
        OrchestrationMessage.ApplicationWindowPositioned(
            WindowId(handle.toString()),
            windowPos.x, windowPos.y, width, height, false
        ).sendAsync()
    }


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

}


private fun glfwGetCursorPos(window: Long): Offset {
    val x = DoubleArray(1)
    val y = DoubleArray(1)
    glfwGetCursorPos(window, x, y)
    return Offset(x[0].toFloat(), y[0].toFloat())
}