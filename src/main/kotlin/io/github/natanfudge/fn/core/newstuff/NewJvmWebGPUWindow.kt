package io.github.natanfudge.fn.core.newstuff

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

private var contextIndex = 0

class NewWebGPUContext(
    val window: NewGlfwWindow,
) : AutoCloseable, WebGPUContext {
    override val context = wgpu.getNativeSurface(window.handle)
    private val adapter = wgpu.requestAdapter(context)
        ?.also { context.computeSurfaceCapabilities(it) }
        ?: error("Could not get wgpu adapter")

    var error: WebGPUException? = null

    override val presentationFormat = context.supportedFormats.first()
    override val device = runBlocking {
        adapter.requestDevice(
            DeviceDescriptor(onUncapturedError = {
                throw WebGPUException(it)
            })
        ).getOrThrow()
    }

    override fun close() {
        closeAll(context, adapter, device)
    }
}


class NewWebGPUException(error: GPUError) : Exception("WebGPU Error: $error")


private var frame = 0

data class WebGPUFrame(
    val ctx: WebGPUContext,
    val dimensions: GlfwWindowDimensions,
    val deltaMs: Double,
) : AutoCloseable {
    /**
     * Used by FunFrame to avoid drawing twice. Not optimal because we gonna have issues with multiple consumers of this WebGPUFrame, but for now
     * just the single FunFrame consumer is fine.
     */
    var isReady = true
    // Interestingly, this call (context.getCurrentTexture()) invokes VSync (so it stalls here usually)
    // It's important to call this here and not nearby any user code, as the thread will spend a lot of time here,
    // and so if user code both calls this method and changes something, they are at great risk of a crash on DCEVM reload, see
    // https://github.com/JetBrains/JetBrainsRuntime/issues/534
    private val underlyingWindowFrame = ctx.context.getCurrentTexture()
    val windowTexture = underlyingWindowFrame.texture.createView(descriptor = TextureViewDescriptor(label = "Frame Texture #${frame++}"))
//    val ctx: WebGPUContext =

    override fun close() {
        windowTexture.close()
        underlyingWindowFrame.texture.close()
    }
}

class NewWebGPUSurface(val window: NewGlfwWindow): NewFun("WebGPUWindow", window) {
    val size = window.size
    init {
        sideEffect(Unit) {
            LibraryLoader.load()
            wgpuSetLogLevel(WGPULogLevel_Info)
            val callback = WGPULogCallback.allocate(globalMemory) { level, cMessage, _ ->
                val message = cMessage?.data?.toKString(cMessage.length) ?: "empty message"
                println("$level: $message")
            }
            wgpuSetLogCallback(callback, globalMemory.bufferOfAddress(callback.handler).handler)
        }


        events.windowResized.listen { (width, height) ->
            webgpu.context.configure(
                SurfaceConfiguration(
                    webgpu.device, format = webgpu.presentationFormat
                ),
                width = width.toUInt(), height = height.toUInt()
            )
        }
    }

    lateinit var webgpu: NewWebGPUContext
    override fun init() {
        webgpu = NewWebGPUContext(window)
    }

    override fun cleanup() {
        webgpu.close()
    }

//    val frameLifecycle = window.frameLifecycle.bind(dimensionsLifecycle, "WebGPU Frame", FunLogLevel.Verbose) { frame, dim ->
//        WebGPUFrame(ctx = dim.surface, dimensions = dim.dimensions, deltaMs = frame.deltaMs)
//    }


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
