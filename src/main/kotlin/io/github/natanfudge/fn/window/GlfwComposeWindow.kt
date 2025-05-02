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

//TODO: 1.  fix input
// 2. Fix window resize
// 3. Refactor GlInitComposeGlfwAdapter
// 4. Add a mechanism for checking for FPS drops
// 5. Add some performance monitoring

typealias ComposeFrameCallback = (bytes: ByteArray, width: Int,height: Int) -> Unit

class GlfwComposeWindow(
    show: Boolean = false,
) {
    private val window = GlfwFunWindow(GlfwConfig(disableApi = false, showWindow = show), name = "Compose")

    val dispatcher = GlfwCoroutineDispatcher()

    private lateinit var compose: GlInitComposeGlfwAdapter
    var windowHandle: Long = 0

    private var pendingResizes = 0

    private var justResized = false

    /**
     * When the window is resizing, we want to wait for it to complete before serving the next frame in [getFrame]
     */
//    private val windowResizingLock = Semaphore(1)

    var less = 0

//    private var pendingFrame: ((ByteArray) -> Unit)? = null

    private var onFrameReady: ComposeFrameCallback = {_,_,_ ->}

    fun onFrameReady(callback: ComposeFrameCallback) {
        this.onFrameReady = callback
    }

//    /**
//     * WIll return false if the frame is not ready yet
//     */
//    fun getFrame(usage: (ByteArray?, frameWidth: Int, frameHeight: Int) -> Unit): Boolean {
//        // Wait for possible resizings to occur
////        println("Resizes at frame: $pendingResizes")
//        println("Checking resize: $pendingResizes")
//        if (pendingResizes > 0 || !::compose.isInitialized) {
//            return false
//        }
////        while (pendingResizes > 0 ) {
////            if(less++ % 500 == 0) {
////                println("Waiting for yield")
////            }
////            TODO: should prob use semaphore
////            return
////            Thread.yield()
////        }
////        if (::compose.isInitialized) {
//        compose.getFrame(usage)
//        return true
////        } else {
////            usage(null, 0, 0)
////        }
//    }


    private var onResizeComplete: () -> Unit = {}
//    private var onResizeCom
    /**
     * Will be called on window's thread
     */
    fun onResizeComplete(callback: () -> Unit) {
        this.onResizeComplete = callback
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
//            window.submitTask {
                compose.scene.sendPointerEvent(eventType, position, scrollDelta, timeMillis, type, buttons, keyboardModifiers, nativeEvent, button)
//            }
        }

        override fun keyEvent(event: KeyEvent) {
            if (!initialized) return
//            window.submitTask {
                compose.scene.sendKeyEvent(event)
//            }
        }

        override fun resize(width: Int, height: Int) {
            if (!initialized) return

            // Please wait - i'm resizing the window
            pendingResizes++
            justResized = true
            println("Resizes after increment: $pendingResizes")
//            windowResizingLock.acquire()
//            window.submitTask {
                println("Compose width set to $width")
                // Resize dummy window to match
                GLFW.glfwSetWindowSize(window.handle, width, height)
                compose.resize(width, height)
                pendingResizes--
//                if (pendingResizes == 0) {
//                    onResizeComplete()
//                }
                println("Resizes after decrement: $pendingResizes")
                // It's OK we can keep rendering now
//                if (windowResizingLock.isLocked) {
//                    windowResizingLock.unlock()
//                }
//            }
        }

        override fun densityChange(newDensity: Density) {
            if (!initialized) return
//            window.submitTask {
                compose.scene.density = newDensity
//            }
        }

        override fun windowClosePressed() {
            if (!initialized) return
//            window.submitTask {
                window.close()
                compose.close()
//            }
        }

        override fun AutoClose.frame(delta: Double) {
            dispatcher.poll()
            if (compose.invalid) {
                GLFW.glfwMakeContextCurrent(windowHandle)
                compose.draw()
                glfwSwapBuffers(windowHandle)
                compose.invalid = false
            }
        }
    }

//    private val waitingTasks


    @OptIn(InternalComposeUiApi::class)
    fun show(config: WindowConfig) {
        window.show(config, object : WindowCallbacks {
            override fun init(handle: WindowHandle) {
                windowHandle = handle
                compose = GlInitComposeGlfwAdapter(
                    config.initialWindowWidth, config.initialWindowHeight, dispatcher,
                    density = Density(glfwGetWindowContentScale(handle)),
                    composeContent = { ComposeMainApp() },
                    onFrameReady = {b, w, h ->
                        onFrameReady(b,w,h)
//                        if (justResized) {
//                            onResizeComplete()
//                            justResized = false
//                        }
                    }
                )
                initialized = true
            }

        }, loop = false)
    }

    fun restart(config: WindowConfig = WindowConfig()) {
        window.restart(config)
    }

//    fun submitRestart(config: WindowConfig = WindowConfig()) {
//        window.submitTask {
//            window.restart(config)
//        }
//    }
}


private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}