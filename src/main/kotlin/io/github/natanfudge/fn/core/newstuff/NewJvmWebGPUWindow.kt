package io.github.natanfudge.fn.core.newstuff

import androidx.compose.ui.unit.IntSize
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import darwin.CAMetalLayer
import darwin.NSWindow
import ffi.LibraryLoader
import ffi.globalMemory
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.WebGPUException
import io.github.natanfudge.fn.webgpu.WebGPUWindow.Companion.wgpu
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

var nextWgpuIndex = 0

class NewWebGPUContext(
    val window: NewGlfwWindowHolder,
) : InvalidationKey(), WebGPUContext {
    override val surface = wgpu.getNativeSurface(window.handle)
    private val adapter = wgpu.requestAdapter(this@NewWebGPUContext.surface)
        ?.also { this@NewWebGPUContext.surface.computeSurfaceCapabilities(it) }
        ?: error("Could not get wgpu adapter")


    val index = nextWgpuIndex++

    init {
        println("Init device num $index")
    }

    var error: WebGPUException? = null

    override val presentationFormat = this@NewWebGPUContext.surface.supportedFormats.first()
    override val device = runBlocking {
        adapter.requestDevice(
            DeviceDescriptor(onUncapturedError = {
                throw WebGPUException(it)
            }, label = "Device-${index}")
        ).getOrThrow()
    }

    val refreshRate = getRefreshRate(window.handle)

    internal fun configure(size: IntSize) {
        this@NewWebGPUContext.surface.configure(
            SurfaceConfiguration(
                device, format = presentationFormat
            ),
            width = size.width.toUInt(), height = size.height.toUInt()
        )
    }

    init {
        configure(window.size)
    }

    override fun close() {
        println("Closing device $nextWgpuIndex")
        closeAll(this@NewWebGPUContext.surface, adapter, device)
    }
}


var surfaceHolderNextIndex = 0

//TODo: I think get rid of the "reload with set in constructor paradigm because it's usually wrong. Usages of sideEffect() with a non-Fun are usually right.
data class NewWebGPUSurfaceHolder(val windowHolder: NewGlfwWindowHolder) : NewFun("WebGPUSurface") {
    val size get() = windowHolder.size

    val index = surfaceHolderNextIndex++

    @Suppress("unused")
    val wgpuInit by cached(InvalidationKey.None) {
        LibraryLoader.load()
        wgpuSetLogLevel(WGPULogLevel_Info)
        val callback = WGPULogCallback.allocate(globalMemory) { level, cMessage, _ ->
            val message = cMessage?.data?.toKString(cMessage.length) ?: "empty message"
            println("$level: $message")
        }
        wgpuSetLogCallback(callback, globalMemory.bufferOfAddress(callback.handler).handler)
    }

    val surface by cached(windowHolder.effect) {
        NewWebGPUContext(windowHolder)
    }

//    val surfaceKey get()  = ::surface.getBackingEffect()

    override fun toString(): String {
        return "WebGPUSurface #$index holding context #${surface.index}"
    }


    init {
        events.windowResized.listen {
            if (it.width != 0 && it.height != 0) {
                this@NewWebGPUSurfaceHolder.surface.configure(it)
            }
        }
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

/**
 * Currently will give the refresh rate of the initial window until this is fixed: https://github.com/gfx-rs/wgpu/issues/7663
 */
private fun getRefreshRate(window: Long): Int {
    // Get the monitor the window is currently on
    var monitor = glfwGetWindowMonitor(window)
    if (monitor == 0L) monitor = GLFW.glfwGetPrimaryMonitor()
    if (monitor == 0L) error("Could not get any monitor for refresh rate")
    val vidMode = glfwGetVideoMode(monitor) ?: error("Failed to get video mode")

    return vidMode.refreshRate()
}
