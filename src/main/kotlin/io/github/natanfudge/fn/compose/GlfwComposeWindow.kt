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
    context: DirectContext,
) : AutoCloseable {
    // Skia Surface, bound to the OpenGL context
    val surface = glDebugGroup(0, groupName = { "Compose Surface Init" }) {
        createSurface(width, height, context)
    }
    val canvas = surface.canvas.asComposeCanvas()

    val frame = ByteArray(width * height * 4)
    val frameByteBuffer = MemoryUtil.memAlloc(width * height * 4)

    var invalid = true

    override fun close() {
        surface.close()
        MemoryUtil.memFree(frameByteBuffer)
    }
}

@OptIn(InternalComposeUiApi::class)
class ComposeGlfwWindow(
    initialWidth: Int,
    initialHeight: Int,
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

class GlfwComposeWindow(
    host: GlfwWindowConfig,
    val content: @Composable () -> Unit = { Text("Hello!") },
    show: Boolean = false,
) {
    private val glfw = GlfwWindowConfig(GlfwConfig(disableApi = false, showWindow = show), name = "Compose")

    // We want this one to start early so we can update its size with the dimensions lifecycle afterwards
    val windowLifecycle = glfw.windowLifecycle.bind("Compose Window", early = true) {
        glDebugGroup(1, groupName = { "Compose Init" }) {
            ComposeGlfwWindow(
                it.init.initialWindowWidth, it.init.initialWindowHeight,
                density = Density(glfwGetWindowContentScale(it.handle)),
                content
            ) {
                dimensions.invalid = true
            }
        }
    }

    val window: ComposeGlfwWindow by windowLifecycle
    val frame = MutEventStream<ComposeFrameEvent>()

    val dimensions by glfw.dimensionsLifecycle.bind("Compose Fixed Size Window") {
        GLFW.glfwSetWindowSize(it.handle, it.width, it.height)
        window.scene.size = IntSize(it.width, it.height)

        FixedSizeComposeWindow(it.width, it.height, window.context)
    }

    init {
        // Make sure we get the frame early so we can draw it in the webgpu pass of the current frame
        host.frameLifecycle.bind("Compose Frame Store", FunLogLevel.Verbose, early = true) {
            frame()
        }
    }

    private fun frame() {
        window.dispatcher.poll()
        if (dimensions.invalid) {
            GLFW.glfwMakeContextCurrent(this@GlfwComposeWindow.glfw.handle)
            draw()
            glfwSwapBuffers(this@GlfwComposeWindow.glfw.handle)
            dimensions.invalid = false
        }
    }


    private fun draw() = glDebugGroup(5, groupName = { "Compose Render" }) {
        try {
            // When updates are needed - render new content
            renderSkia()
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

        glReadPixels(0, 0, dimensions.width, dimensions.height, GL_RGBA, GL_UNSIGNED_BYTE, dimensions.frameByteBuffer)
        val buffer = getFrameBytes()
        buffer.get(dimensions.frame)

        frame.emit(ComposeFrameEvent(dimensions.frame, dimensions.width, dimensions.height))
    }


    private fun renderSkia() {
        // Set color explicitly because skia won't reapply it every time
        glClearColor(0f, 0f, 0f, 0f)
        glDebugGroup(2, groupName = { "Compose Canvas Clear" }) {
            dimensions.surface.canvas.clear(Color.TRANSPARENT)
        }

        // Render to the framebuffer
        glDebugGroup(3, groupName = { "Compose Render Content" }) {
            window.scene.render(dimensions.canvas, System.nanoTime())
        }
        glDebugGroup(4, groupName = { "Compose Flush" }) {
            window.context.flush()
        }
    }

    private fun getFrameBytes(): ByteBuffer {
        val width = dimensions.width
        val height = dimensions.height

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
            if (!windowLifecycle.isInitialized) return


            // Resize dummy window to match
            GLFW.glfwSetWindowSize(this@GlfwComposeWindow.glfw.handle, width, height)
        }

        override fun densityChange(newDensity: Density) {
            if (!windowLifecycle.isInitialized) return
            window.scene.density = newDensity
        }
    }


    @OptIn(InternalComposeUiApi::class)
    fun show(config: WindowConfig) {
        glfw.show(config, object : RepeatingWindowCallbacks {}, loop = false)
    }

    fun restart(config: WindowConfig = WindowConfig()) {
        glfw.restart(config)
    }
}


private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}