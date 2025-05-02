package io.github.natanfudge.fn.webgpu

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.Density
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import darwin.CAMetalLayer
import darwin.NSWindow
import ffi.LibraryLoader
import ffi.globalMemory
import io.github.natanfudge.fn.window.*
import io.ygdrasil.webgpu.*
import io.ygdrasil.wgpu.WGPULogCallback
import io.ygdrasil.wgpu.WGPULogLevel_Info
import io.ygdrasil.wgpu.wgpuSetLogCallback
import io.ygdrasil.wgpu.wgpuSetLogLevel
import org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow
import org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandDisplay
import org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandWindow
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window
import org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display
import org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window
import org.rococoa.ID
import org.rococoa.Rococoa

data class WebGPUContext(
    val context: NativeSurface,
    val adapter: GPUAdapter,
    val presentationFormat: GPUTextureFormat,
) : AutoClose {
    private val autoClose = AutoCloseImpl()
    override val <T : AutoCloseable> T.ac: T
        get() {
            autoClose.toClose.add(this)
            return this
        }

    override fun close() {
        autoClose.close()
        context.close()
        adapter.close()
    }
}



@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
class WebGPUWindow(
    private val init: WebGPUContext.() -> RepeatingWindowCallbacks,
) : AutoCloseable {
    private val window = GlfwFunWindow(GlfwConfig(disableApi = true, showWindow = true), name = "WebGPU")

    init {
        LibraryLoader.load()
        wgpuSetLogLevel(WGPULogLevel_Info)
        val callback = WGPULogCallback.allocate(globalMemory) { level, cMessage, userdata ->
            val message = cMessage?.data?.toKString(cMessage.length) ?: "empty message"
            println("$level: $message")
        }
        wgpuSetLogCallback(callback, globalMemory.bufferOfAddress(callback.handler).handler)
    }

    private val wgpu = WGPU.createInstance() ?: error("failed to create wgpu instance")

    private lateinit var context: WebGPUContext

    private lateinit var _userCallbacks: RepeatingWindowCallbacks

    private var minimized = false


    fun show(config: WindowConfig) {
        window.show(config, object : WindowCallbacks {
            override fun init(handle: WindowHandle) {
                val nativeSurface = wgpu.getNativeSurface(handle)
                val adapter = wgpu.requestAdapter(nativeSurface) ?: error("Could not get wgpu adapter")
                nativeSurface.computeSurfaceCapabilities(adapter)
                val format = nativeSurface.supportedFormats.first()
                context = WebGPUContext(nativeSurface, adapter, format)
                _userCallbacks = init(context)
            }

            override fun AutoClose.frame(delta: Double) {
//                println("Pre-minimized frame")
                if (!minimized) {
                    with(_userCallbacks) {
//                        println("WEbGPU Frame")
                        frame(delta)
                    }
                }
            }

            override fun resize(width: Int, height: Int) {
                if (width == 0 && height == 0) {
                    minimized = true
                } else {
                    minimized = false
                    _userCallbacks.resize(width, height)
                }
            }

            override fun windowClosePressed() {
                _userCallbacks.windowClosePressed()
                close()
            }

            override fun setMinimized(minimized: Boolean) {
                _userCallbacks.setMinimized(minimized)
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
                button: PointerButton?,
            ) {
                _userCallbacks.pointerEvent(eventType, position, scrollDelta, timeMillis, type, buttons, keyboardModifiers, nativeEvent, button)
            }

            override fun keyEvent(event: KeyEvent) {
                _userCallbacks.keyEvent(event)
            }

            override fun densityChange(newDensity: Density) {
                _userCallbacks.densityChange(newDensity)
            }

        })

    }

    val open get() = window.open

    //TODO: unsphagetti
    fun frame() {
        window.frame()
    }

    fun pollTasks() {
        window.pollTasks()
    }


    /**
     * Submits a callback to run on the main thread.
     */
    fun submitTask(task: () -> Unit) {
        window.submitTask(task)
    }

    fun restart(config: WindowConfig= WindowConfig()) {
        context.close()
        window.restart(config)
    }


    override fun close() {
        window.close()
        context.close()
    }
}

private enum class Os {
    Linux,
    Window,
    MacOs
}


private val os = System.getProperty("os.name").let { name ->
    when {
        arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } -> Os.Linux
        arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) } -> Os.MacOs
        arrayOf("Windows").any { name.startsWith(it) } -> Os.Window
        else -> error("Unrecognized or unsupported operating system.")
    }
}

private fun WGPU.getNativeSurface(window: Long): NativeSurface = when (os) {
    Os.Linux -> when {
        glfwGetWaylandWindow(window) == 0L -> {
            println("running on X11")
            val display = glfwGetX11Display().toNativeAddress()
            val x11_window = glfwGetX11Window(window).toULong()
            getSurfaceFromX11Window(display, x11_window) ?: error("fail to get surface on Linux")
        }

        else -> {
            println("running on Wayland")
            val display = glfwGetWaylandDisplay().toNativeAddress()
            val wayland_window = glfwGetWaylandWindow(window).toNativeAddress()
            getSurfaceFromWaylandWindow(display, wayland_window)
        }
    }

    Os.Window -> {
        val hwnd = glfwGetWin32Window(window).toNativeAddress()
        val hinstance = Kernel32.INSTANCE.GetModuleHandle(null).pointer.toNativeAddress()
        getSurfaceFromWindows(hinstance, hwnd) ?: error("fail to get surface on Windows")
    }

    Os.MacOs -> {
        val nsWindowPtr = glfwGetCocoaWindow(window)
        val nswindow = Rococoa.wrap(ID.fromLong(nsWindowPtr), NSWindow::class.java)
        nswindow.contentView()?.setWantsLayer(true)
        val layer = CAMetalLayer.layer()
        nswindow.contentView()?.setLayer(layer.id().toLong().toPointer())
        getSurfaceFromMetalLayer(layer.id().toLong().toNativeAddress())
    }
} ?: error("fail to get surface")


private fun Long.toPointer(): Pointer = Pointer(this)
