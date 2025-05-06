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
import io.github.natanfudge.fn.util.bindBindable
import io.github.natanfudge.fn.util.bindHighPriorityBindable
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.WebGPUWindow.Companion.wgpu
import io.github.natanfudge.fn.window.*
import io.ygdrasil.webgpu.*
import io.ygdrasil.wgpu.WGPULogCallback
import io.ygdrasil.wgpu.WGPULogLevel_Info
import io.ygdrasil.wgpu.wgpuSetLogCallback
import io.ygdrasil.wgpu.wgpuSetLogLevel
import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwGetVideoMode
import org.lwjgl.glfw.GLFW.glfwGetWindowMonitor
import org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow
import org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandDisplay
import org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandWindow
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window
import org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display
import org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window
import org.rococoa.ID
import org.rococoa.Rococoa

class WebGPUContext(
    handle: Long,
) : AutoCloseable {
    val context = wgpu.getNativeSurface(handle)
    val adapter = wgpu.requestAdapter(context)
        ?.also { context.computeSurfaceCapabilities(it) }
        ?: error("Could not get wgpu adapter")

    val presentationFormat = context.supportedFormats.first()
    val device = runBlocking {
        adapter.requestDevice(
            DeviceDescriptor()
        ).getOrThrow()
    }
    /**
     * Currently will give the refresh rate of the initial window until this is fixed: https://github.com/gfx-rs/wgpu/issues/7663
     */
    val refreshRate = getRefreshRate(handle)

    override fun close() {
        closeAll(context, adapter, device)
    }
}


class WebGPUWindow {
    companion object {
        init {
            LibraryLoader.load()
            wgpuSetLogLevel(WGPULogLevel_Info)
            val callback = WGPULogCallback.allocate(globalMemory) { level, cMessage, userdata ->
                val message = cMessage?.data?.toKString(cMessage.length) ?: "empty message"
                println("$level: $message")
            }
            wgpuSetLogCallback(callback, globalMemory.bufferOfAddress(callback.handler).handler)
        }

        val wgpu = WGPU.createInstance() ?: error("failed to create wgpu instance")
    }

    private val window = GlfwWindowConfig(GlfwConfig(disableApi = true, showWindow = true), name = "WebGPU")


    // Surface needs to initialize before the dimensions
    val surfaceLifecycle = window.windowLifecycle.bindHighPriorityBindable("WebGPU Surface") {
        WebGPUContext(it.handle)
    }

    val dimensionsLifecycle = window.dimensionsLifecycle.bindBindable("WebGPU resize") { dim ->
        surface.context.configure(
            SurfaceConfiguration(
                surface.device, format = surface.presentationFormat
            ),
            width = dim.width.toUInt(), height = dim.height.toUInt()
        )
        dim
    }


    private val surface by surfaceLifecycle

    val frameLifecycle = window.frameLifecycle


    fun show(config: WindowConfig, callbackHook: RepeatingWindowCallbacks? = null) {
        val baseCallbacks = object : RepeatingWindowCallbacks {
            override fun AutoClose.frame(deltaMs: Double) {
                surface.context.present()
            }
        }
        val callbacks = callbackHook?.combine(baseCallbacks) ?: baseCallbacks
        window.show(config, callbacks)
    }

    /**
     * Submits a callback to run on the main thread.
     */
    fun submitTask(task: () -> Unit) {
        window.submitTask(task)
    }

    fun restart(config: WindowConfig = WindowConfig()) {
        surface.close()

        window.restart(config)
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
private fun getRefreshRate(window: Long): Int {
    // Get the monitor the window is currently on
    var monitor = glfwGetWindowMonitor(window)
    if (monitor == 0L) monitor = GLFW.glfwGetPrimaryMonitor()
    if (monitor == 0L) error("Could not get any monitor for refresh rate")
    val vidMode = glfwGetVideoMode(monitor) ?: error("Failed to get video mode")

    return vidMode.refreshRate()
}
