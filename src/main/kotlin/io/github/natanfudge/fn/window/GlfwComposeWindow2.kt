package io.github.natanfudge.fn.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.compose.GlInitComposeGlfwAdapter
import io.github.natanfudge.fn.webgpu.AutoClose
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import org.lwjgl.glfw.GLFW.glfwGetWindowContentScale
import org.lwjgl.glfw.GLFW.glfwSetCharCallback
import org.lwjgl.glfw.GLFW.glfwSetCursorEnterCallback
import org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback
import org.lwjgl.glfw.GLFW.glfwSetKeyCallback
import org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback
import org.lwjgl.glfw.GLFW.glfwSetScrollCallback
import org.lwjgl.glfw.GLFW.glfwSetWindowContentScaleCallback
import org.lwjgl.glfw.GLFW.glfwSwapBuffers

fun main() {
    GlfwComposeWindow2().show(WindowConfig())
}


class GlfwComposeWindow2 {
    private val window = GlfwFunWindow(GlfwConfig(disableApi = false, showWindow = true))

    val dispatcher = GlfwCoroutineDispatcher()

    private lateinit var compose: GlInitComposeGlfwAdapter
    var windowHandle: Long = 0


    @OptIn(InternalComposeUiApi::class)
    fun show(config: WindowConfig) {
        window.show(config, object : WindowCallbacks {
            override fun init(handle: WindowHandle) {
                windowHandle = handle
                compose = GlInitComposeGlfwAdapter(
                    config.initialWindowWidth, config.initialWindowHeight, dispatcher,
                    density = Density(glfwGetWindowContentScale(handle)),
                    composeContent = { ComposeMainApp() }
                )
            }

            override fun pointerEvent(
                eventType: PointerEventType,
                position: Offset,
                scrollDelta: Offset,
                timeMillis: Long,
                type: PointerType,
                buttons: PointerButtons?,
                keyboardModifiers: PointerKeyboardModifiers?,
                nativeEvent: Any?,
                button: PointerButton?
            ) {
                compose.scene.sendPointerEvent(eventType, position, scrollDelta, timeMillis, type, buttons, keyboardModifiers, nativeEvent, button)
            }

            override fun keyEvent(event: KeyEvent) {
                compose.scene.sendKeyEvent(event)
            }

            override fun resize(width: Int, height: Int) {
                compose.resize(width, height)
            }

            override fun densityChange(newDensity: Density) {
                compose.scene.density = newDensity
            }

            override fun AutoClose.frame(delta: Double) {
                dispatcher.poll()
                if (compose.invalid) {
                    compose.draw()
                    glfwSwapBuffers(windowHandle)
                    compose.invalid = false
                }
            }

            override fun windowClosePressed() {
                window.close()
                compose.close()
            }
        })
    }

    fun restart(config: WindowConfig = WindowConfig()) {
        window.restart(config)
    }
}


private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}