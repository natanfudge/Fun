package io.github.natanfudge.fn.compose


import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.window.ComposeFrameCallback
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.skia.*
import org.jetbrains.skia.FramebufferFormat.Companion.GR_GL_RGBA8
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glReadPixels
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL43.GL_FRAMEBUFFER_BINDING
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer


@OptIn(InternalComposeUiApi::class)
internal class GlInitComposeGlfwAdapter(
    initialWidth: Int,
    initialHeight: Int,
    dispatcher: CoroutineDispatcher,
    private val density: Density,
    private val composeContent: @Composable () -> Unit,
    private val onFrameReady: ComposeFrameCallback,
) {
    @OptIn(InternalComposeUiApi::class)
    private inner class GlInitFixedSizeComposeWindow(
        val width: Int,
        val height: Int,
        context: DirectContext,
    ) {

        // Skia Surface, bound to the OpenGL context
        val surface = glDebugGroup(0, groupName = { "Compose Surface Init" }) {
            createSurface(width, height, context)
        }
        val canvas = surface.canvas.asComposeCanvas()

        val frame = ByteArray(width * height * 4)
        val frameByteBuffer = MemoryUtil.memAlloc(width * height * 4)

        fun close() {
            surface.close()
            MemoryUtil.memFree(frameByteBuffer)
        }
    }

    val context = DirectContext.makeGL()


    private lateinit var window: GlInitFixedSizeComposeWindow

    init {
        init(initialWidth, initialHeight)
    }

    val frameDispatcher = FrameDispatcher(dispatcher) {
        // Draw new skia content
        invalid = true
    }

    val scene = PlatformLayersComposeScene(
        coroutineContext = dispatcher,
        density = density,
        invalidate = frameDispatcher::scheduleFrame,
        size = IntSize(initialWidth, initialHeight)
    )

    /**
     * Depends on the [window], must be updated when the [window] updates.
     */
    init {
        scene.setContent {
            composeContent()
        }
    }


    private fun init(width: Int, height: Int) {
        glDebugGroup(1, groupName = { "Skia Init" }) {
            window = GlInitFixedSizeComposeWindow(width, height, context)
        }
    }


    var invalid = true

    fun draw() = glDebugGroup(5, groupName = { "Compose Render" }) {
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

        glReadPixels(0, 0, window.width, window.height, GL_RGBA, GL_UNSIGNED_BYTE, window.frameByteBuffer)
        val buffer = getFrameBytes()
        buffer.get(window.frame)

        onFrameReady(window.frame, window.width, window.height)
    }


    private fun renderSkia() {
        // Set color explicitly because skia won't reapply it every time
        glClearColor(0f, 0f, 0f, 0f)
        glDebugGroup(2, groupName = { "Compose Canvas Clear" }) {
            window.surface.canvas.clear(Color.TRANSPARENT)
        }

        // Render to the framebuffer
        glDebugGroup(3, groupName = { "Compose Render Content" }) {
            scene.render(window.canvas, System.nanoTime())
        }
        glDebugGroup(4, groupName = { "Compose Flush" }) {
            context.flush()
        }
    }

    private fun getFrameBytes(): ByteBuffer {
        val width = window.width
        val height = window.height

        val buffer = MemoryUtil.memAlloc(width * height * 4)
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        return buffer
    }


    fun resize(width: Int, height: Int) = glDebugGroup(500, groupName = { "Skia Resize" }) {
        scene.size = IntSize(width, height)
        window.close()
        window = GlInitFixedSizeComposeWindow(width, height, context)
        invalid = true
    }

    fun close() {
        scene.close()
        window.close()
    }
}

/**
 * Below is the list of OpenGL state damage that Skia does by calling Surface.makeFromBackendRenderTarget.
 * These state changes may harm our program.
 * ```
 * glDisable(GL_DEPTH_TEST)
 * glDepthMask(False)
 * glDisable(GL_CULL_FACE)
 * glFrontFace(GL_CCW)
 * glDisable(GL_POLYGON_OFFSET_FILL)
 * glEnable(GL_VERTEX_PROGRAM_POINT_SIZE)
 * glLineWidth(1.00)
 * glDisable(GL_DITHER)
 * glEnable(GL_MULTISAMPLE)
 * glPixelStorei(GL_UNPACK_ROW_LENGTH, 0)
 * glPixelStorei(GL_PACK_ROW_LENGTH, 0)
 * ```
 */
private fun createSurface(width: Int, height: Int, context: DirectContext): Surface {
    val fbId = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING)
    val renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, fbId, GR_GL_RGBA8)

    return Surface.makeFromBackendRenderTarget(
        context, renderTarget, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.sRGB
    )!!
}

