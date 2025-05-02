package io.github.natanfudge.fn.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.Density
import io.github.natanfudge.fn.compose.ComposeMainApp
import io.github.natanfudge.fn.compose.GlInitComposeGlfwAdapter
import io.github.natanfudge.fn.webgpu.AutoClose
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwGetWindowContentScale
import org.lwjgl.glfw.GLFW.glfwSwapBuffers

fun main() {
    GlfwComposeWindow().show(WindowConfig())
}

//TODO:
// 3. Refactor GlInitComposeGlfwAdapter
// 4. Add a mechanism for checking for FPS drops
// 5. Add some performance monitoring

typealias ComposeFrameCallback = (bytes: ByteArray, width: Int, height: Int) -> Unit

class GlfwComposeWindow(
    show: Boolean = false,
) {
    private val window = GlfwFunWindow(GlfwConfig(disableApi = false, showWindow = show), name = "Compose")

    val dispatcher = GlfwCoroutineDispatcher()

    private lateinit var compose: GlInitComposeGlfwAdapter


    private var onFrameReady: ComposeFrameCallback = { _, _, _ -> }

    fun onFrameReady(callback: ComposeFrameCallback) {
        this.onFrameReady = callback
    }

    private var initialized = false

    @OptIn(InternalComposeUiApi::class)
    val callbacks = object : RepeatingWindowCallbacks {
        override fun pointerEvent(
            eventType: PointerEventType,
            position: Offset,
            scrollDelta: Offset,
            timeMillis: Long,
            type: PointerType,
            buttons: PointerButtons?,
            keyboardModifiers: PointerKeyboardModifiers?,
            nativeEvent: Any?,
            button: PointerButton?,
        ) {
            if (!initialized) return
            compose.scene.sendPointerEvent(eventType, position, scrollDelta, timeMillis, type, buttons, keyboardModifiers, nativeEvent, button)
        }

        override fun keyEvent(event: KeyEvent) {
            if (!initialized) return
            compose.scene.sendKeyEvent(event)
        }

        override fun resize(width: Int, height: Int) {
            if (!initialized) return


            // Resize dummy window to match
            GLFW.glfwSetWindowSize(window.handle, width, height)
            compose.resize(width, height)

        }

        override fun densityChange(newDensity: Density) {
            if (!initialized) return
            compose.scene.density = newDensity
        }

        override fun windowClosePressed() {
            if (!initialized) return
            window.close()
            compose.close()
        }

        override fun AutoClose.frame(delta: Double) {
            dispatcher.poll()
            if (compose.invalid) {
                GLFW.glfwMakeContextCurrent(window.handle)
                compose.draw()
                glfwSwapBuffers(window.handle)
                compose.invalid = false
            }
        }
    }


    @OptIn(InternalComposeUiApi::class)
    fun show(config: WindowConfig) {
        window.show(config, object : WindowCallbacks {
            override fun init(handle: WindowHandle) {
                compose = GlInitComposeGlfwAdapter(
                    config.initialWindowWidth, config.initialWindowHeight, dispatcher,
                    density = Density(glfwGetWindowContentScale(handle)),
                    composeContent = { ComposeMainApp() },
                    onFrameReady = { b, w, h ->
                        onFrameReady(b, w, h)
                    }
                )
                initialized = true
            }

        }, loop = false)
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