package io.github.natanfudge.fn.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.*

@OptIn(InternalComposeUiApi::class)
fun ComposeScene.subscribeToGLFWEvents(windowHandle: Long) {
    glfwSetMouseButtonCallback(windowHandle) { _, button, action, mods ->
        // We're going to send the clicks to the GUI even if it is not focused, to be able to actually move from opengl focus to Compose focus.
        sendPointerEvent(
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
        sendPointerEvent(
            position = Offset(xpos.toFloat(), ypos.toFloat()),
            eventType = PointerEventType.Move,
            nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))
        )
    }

    glfwSetScrollCallback(windowHandle) { window, xoffset, yoffset ->
        sendPointerEvent(
            eventType = PointerEventType.Scroll,
            position = glfwGetCursorPos(windowHandle),
            scrollDelta = Offset(xoffset.toFloat(), -yoffset.toFloat()),
            nativeEvent = AwtMouseWheelEvent(getAwtMods(windowHandle))
        )

    }

    glfwSetCursorEnterCallback(windowHandle) { _, entered ->
        sendPointerEvent(
            position = glfwGetCursorPos(windowHandle),
            eventType = if (entered) PointerEventType.Enter else PointerEventType.Exit,
            nativeEvent = AwtMouseEvent(getAwtMods(windowHandle))
        )
    }

    glfwSetKeyCallback(windowHandle) { _, key, scancode, action, mods ->
        val event = glfwToComposeEvent(key, action, mods)
        sendKeyEvent(event)
    }

    glfwSetCharCallback(windowHandle) { window, codepoint ->
        for (char in Character.toChars(codepoint)) {
            sendKeyEvent(typedCharacterToComposeEvent(char))
        }
    }

    glfwSetWindowContentScaleCallback(windowHandle) { _, xscale, _ ->
        density = Density(xscale)
    }
}

private fun glfwGetCursorPos(window: Long): Offset {
    val x = DoubleArray(1)
    val y = DoubleArray(1)
    GLFW.glfwGetCursorPos(window, x, y)
    return Offset(x[0].toFloat(), y[0].toFloat())
}