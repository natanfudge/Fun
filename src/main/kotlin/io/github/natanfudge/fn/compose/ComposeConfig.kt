@file:OptIn(InternalComposeUiApi::class)

package io.github.natanfudge.fn.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.util.FunLogLevel
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.util.MutEventStream
import io.github.natanfudge.fn.window.*
import org.jetbrains.skia.*
import org.jetbrains.skia.FramebufferFormat.Companion.GR_GL_RGBA8
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwGetWindowContentScale
import org.lwjgl.glfw.GLFW.glfwSwapBuffers
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer


@OptIn(InternalComposeUiApi::class)
class FixedSizeComposeWindow(
    val width: Int,
    val height: Int,
    val window: ComposeGlfwWindow,
//    context: DirectContext,
) : AutoCloseable {
    // Skia Surface, bound to the OpenGL context
    val surface = glDebugGroup(0, groupName = { "Compose Surface Init" }) {
        createSurface(width, height, window.context)
    }
    val canvas = surface.canvas.asComposeCanvas()

    val frame = ByteArray(width * height * 4)
    val frameByteBuffer = MemoryUtil.memAlloc(width * height * 4)

    var invalid = true

    override fun toString(): String {
        return "Compose Window w=$width, h=$height"
    }

    override fun close() {
        surface.close()
        MemoryUtil.memFree(frameByteBuffer)
    }
}

@OptIn(InternalComposeUiApi::class)
class ComposeGlfwWindow(
    initialWidth: Int,
    initialHeight: Int,
    val handle: WindowHandle,
    private val density: Density,
    private val composeContent: @Composable () -> Unit,
    private val onInvalidate: () -> Unit,
) : AutoCloseable {


    val context = DirectContext.makeGL()
    val dispatcher = GlfwCoroutineDispatcher()
    val frameDispatcher = FrameDispatcher(dispatcher) {
        // Draw new skia content
        onInvalidate()
    }

    val scene = PlatformLayersComposeScene(
        coroutineContext = dispatcher,
        density = density,
        invalidate = frameDispatcher::scheduleFrame,
        size = IntSize(initialWidth, initialHeight)
    )

    init {
        scene.setContent {
            composeContent()
        }
    }

    override fun close() {
        scene.close()
    }
}

private fun createSurface(width: Int, height: Int, context: DirectContext): Surface {
    val fbId = glGetInteger(GL_FRAMEBUFFER_BINDING)
    val renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, fbId, GR_GL_RGBA8)

    return Surface.makeFromBackendRenderTarget(
        context, renderTarget, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.sRGB
    )!!
}

data class ComposeFrameEvent(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)





class ComposeConfig(
    host: GlfwWindowConfig,
//    hostWebGPURenderer: ComposeWebGPURenderer, // Kinda breaking encapsulation here but I don't have a choice because I need the frame
//// to run AFTER the
    val content: @Composable () -> Unit = { Text("Hello!") },
    show: Boolean = false,
) {
    companion object {
        const val LifecycleLabel = "Compose Window"
    }
    private val glfw = GlfwWindowConfig(GlfwConfig(disableApi = false, showWindow = show), name = "Compose", host.config)

    // We want this one to start early so we can update its size with the dimensions lifecycle afterwards
    val windowLifecycle = glfw.windowLifecycle.bind(LifecycleLabel, early = true) {
        glDebugGroup(1, groupName = { "Compose Init" }) {
            ComposeGlfwWindow(
                it.init.initialWindowWidth, it.init.initialWindowHeight,it.handle,
                density = Density(glfwGetWindowContentScale(it.handle)),
                content
            ) {
                // Invalidate Compose frame on change
                dimensionsLifecycle.assertValue.invalid = true
            }
        }
    }

    val window: ComposeGlfwWindow by windowLifecycle
    val frameStream = MutEventStream<ComposeFrameEvent>()

//    val resizeOnHostWindowResize = host.dimensionsLifecycle.bind("Compose Resize Adapt") {
//        GLFW.glfwSetWindowSize(this@ComposeConfig.glfw.handle, it.width, it.height)
//    }

    //TODO: I know what happened. It's because of the ordering of unblocking. Both "'Compose Fixed Size Window'" and 'Compose Frame Store' are blocked.
    // A node is then chosen randomly to be unblocked, when in fact "'Compose Fixed Size Window'" should always be unblocked first.
    val dimensionsLifecycle: Lifecycle<WindowDimensions, FixedSizeComposeWindow> = host.dimensionsLifecycle.bind2(windowLifecycle,"Compose Fixed Size Window") { dim, window ->
        GLFW.glfwSetWindowSize(dim.handle, dim.width, dim.height)
        window.scene.size = IntSize(dim.width, dim.height)

        FixedSizeComposeWindow(dim.width, dim.height, window)
    }

//    val dimensions by dimensionsLifecycle

    init {
//        val resize = dimensionsLifecycle.bind("Background Window Resize") {
//            GLFW.glfwSetWindowSize(this@ComposeConfig.glfw.handle, it.width, it.height)
//        }
        // Make sure we get the frame early so we can draw it in the webgpu pass of the current frame
        // Also we need
        host.frameLifecycle.bind2(dimensionsLifecycle,"Compose Frame Store", FunLogLevel.Debug, early1 = true) {delta, dim ->
            val window = dim.window
            window.dispatcher.poll()
            if (dim.invalid) {
                GLFW.glfwMakeContextCurrent(window.handle)
                println("Before compose draw")
                dim.draw()
                println("AFter compose draw")
                glfwSwapBuffers(window.handle)
                dim.invalid = false
            }
        }

    }


    private fun FixedSizeComposeWindow.draw() {
        println("Before glDebugGroup")
        glDebugGroup(5, groupName = { "Compose Render" }) {
            println("After glDebugGroup")
            try {
                // When updates are needed - render new content
                renderSkia()
                println("After renderSkia")
            } catch (e: Throwable) {
                System.err.println("Error during Skia rendering! This is usually a Compose user error.")
                e.printStackTrace()
            }

            // SLOW: This method of copying the frame into ByteArrays and then drawing them as a texture is extremely slow, but there
            // is probably no better alternative at the moment. We need some way to draw skia into a WebGPU context.
            // For that we need:
            // A. Skiko vulkan support
            // B. Skiko graphite support for WebGPU support
            // C. Compose skiko vulkan / webgpu / metal support
            // D. Integrate the rendering with each rendering api separately - we need to 'fetch' the vulkan context in our main webgpu app, and draw compose on top of it, and same for the other APIs.

            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, frameByteBuffer)
            println("After glReadPixels")
            val buffer = getFrameBytes()
            println("After getFrameBytes")
            buffer.get(frame)

            println("Pushing new frame")
            frameStream.emit(ComposeFrameEvent(frame, width, height))
        }
    }


    private fun FixedSizeComposeWindow.renderSkia() {
        println("Before glClearColor")
        // Set color explicitly because skia won't reapply it every time
        glClearColor(0f, 0f, 0f, 0f)
        println("After glClearColor")
        glDebugGroup(2, groupName = { "Compose Canvas Clear" }) {
            surface.canvas.clear(Color.TRANSPARENT)
        }
        println("After clear")

        // Render to the framebuffer
        glDebugGroup(3, groupName = { "Compose Render Content" }) {
            window.scene.render(canvas, System.nanoTime())
        }
        println("After render")
        glDebugGroup(4, groupName = { "Compose Flush" }) {
            window.context.flush()
        }
        println("After flush")
    }

    private fun FixedSizeComposeWindow.getFrameBytes(): ByteBuffer {
        val buffer = MemoryUtil.memAlloc(width * height * 4)
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        return buffer
    }


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
            if (!windowLifecycle.isInitialized) return
            window.scene.sendPointerEvent(eventType, position, scrollDelta, timeMillis, type, buttons, keyboardModifiers, nativeEvent, button)
        }

        override fun keyEvent(event: KeyEvent) {
            if (!windowLifecycle.isInitialized) return
            window.scene.sendKeyEvent(event)
        }

        override fun resize(width: Int, height: Int) {
//            if (!windowLifecycle.isInitialized) return
//
//
//            // Resize dummy window to match
//            GLFW.glfwSetWindowSize(this@ComposeConfig.glfw.handle, width, height)
        }

        override fun densityChange(newDensity: Density) {
            if (!windowLifecycle.isInitialized) return
            window.scene.density = newDensity
        }
    }


//    @OptIn(InternalComposeUiApi::class)
//    fun show(config: WindowConfig) {
//        glfw.show(config,  loop = false)
//    }

//    fun restart() {
//        glfw.restart()
//    }
}


private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}