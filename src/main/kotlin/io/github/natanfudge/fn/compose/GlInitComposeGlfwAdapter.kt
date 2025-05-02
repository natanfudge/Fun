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
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL43.GL_FRAMEBUFFER_BINDING
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


@OptIn(InternalComposeUiApi::class)
internal class GlInitComposeGlfwAdapter(
    initialWidth: Int,
    initialHeight: Int,
    dispatcher: CoroutineDispatcher,
    private val density: Density,
    private val composeContent: @Composable () -> Unit,
    private val onFrameReady: ComposeFrameCallback
//    val drawOffscreen: Boolean = false,
) {

    @OptIn(InternalComposeUiApi::class)
    private inner class GlInitFixedSizeComposeWindow(
        val width: Int,
        val height: Int,
        context: DirectContext,
    ) {
        //TODO: currently we put everything on an offscreen framebuffer (with drawOffscreen = true).
        // In practice it might be simple or better to use the main framebuffer.

        // Skia Surface, bound to the OpenGL context
        val surface = glDebugGroup(0, groupName = { "Compose Surface Init" }) {
            createSurface(width, height, context)
        }
        val canvas = surface.canvas.asComposeCanvas()

        val frame = ByteArray(width * height * 4)
        val frameByteBuffer = MemoryUtil.memAlloc(width * height * 4)

//        val colorTexture = if (drawOffscreen) createColorTexture(width, height, { "Compose Color Texture" }) {
//            normalTextureConfig()
//        } else null
//        val depthTexture = if (drawOffscreen) createDepthTexture(width, height, { "Compose Depth Texture" }) {
//            normalTextureConfig()
//        } else null

//        init {
//            if (colorTexture != null && depthTexture != null) {
//                attachColorTextureToFrameBuffer(colorTexture)
//                attachDepthTextureToFrameBuffer(depthTexture)
//            }
//        }

        fun close() {
            surface.close()
            MemoryUtil.memFree(frameByteBuffer)
//            if (colorTexture != null && depthTexture != null) {
//                glDeleteTextures(colorTexture)
//                glDeleteTextures(depthTexture)
//            }
        }
    }

    val context = DirectContext.makeGL()

    //TODO: it may be a good idea to use two "framebuffers"
//    var frame2: ByteBuffer? = null

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


//    private var frameBuffer: Int = 0

    private fun init(width: Int, height: Int) {
        glDebugGroup(1, groupName = { "Skia Init" }) {
//            frameBuffer = createFramebuffer(name = { "Compose Framebuffer" })
//            if (drawOffscreen) {
//                glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer)
//            }
            window = GlInitFixedSizeComposeWindow(width, height, context)
        }
    }

    /**
     * The ByteBuffer will be null if no frame has been created yet
     */
    fun getFrame(usage: (ByteArray, frameWidth: Int, frameHeight: Int) -> Unit) {
        frameLock.withLock {
            usage(window.frame, window.width, window.height)
        }
    }

    private val frameLock = ReentrantLock()


    //TODO: move into the fixedSize thing and close


    var invalid = true

//    var drawToFrame1 = true

    fun draw() = glDebugGroup(5, groupName = { "Compose Render" }) {
        try {
            // When updates are needed - render new content
            renderSkia()
        } catch (e: Throwable) {
            System.err.println("Error during Skia rendering! This is usually a Compose user error.")
            e.printStackTrace()
        }


//        if(drawToFrame1) {
//
//        }

        // SLOW: This method of copying the frame into ByteArrays and then drawing them as a texture is extremely slow, but there
        // is probably no better alternative at the moment. We need some way to draw skia into a WebGPU context.
        // For that we need:
        // A. Skiko vulkan support
        // B. Skiko graphite support for WebGPU support
        // C. Compose skiko vulkan / webgpu / metal support
        // D. Integrate the rendering with each rendering api separately - we need to 'fetch' the vulkan context in our main webgpu app, and draw compose on top of it, and same for the other APIs.

        glReadPixels(0, 0, window.width, window.height, GL_RGBA, GL_UNSIGNED_BYTE, window.frameByteBuffer)
        val buffer = getFrameBytes()
        frameLock.withLock {
            buffer.get(window.frame)
//            if (frame != null) {
//                MemoryUtil.memFree(frame)
//            }
            //SLOW: we should reuse the same ByteArray and only rebuild it when screen size changes
//            frame = ByteArray(window.width * window.height * 4)
        }
        onFrameReady(window.frame, window.width, window.height)


//        if (drawOffscreen) {
//        writeSkiaToImage()
//        }
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

    //TODO: Don't do this bytearray stuff, just keep it a BYteBuffer and free it as you need to
    private fun getFrameBytes(): ByteBuffer {
        val width = window.width
        val height = window.height

        val buffer = MemoryUtil.memAlloc(width * height * 4)
//        try {
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        return buffer
//            val bytes = ByteArray(width * height * 4)
//            buffer.get(bytes)
//            return bytes
//        }
//        finally {
//            MemoryUtil.memFree(buffer)
//        }
    }
//
//    private fun writeSkiaToImage() {
////        val bytes = getFrameBytes()
//        val bytes = frame
//        val width = window.width
//        val height = window.height
//
//        val image = BufferedImage(window.width, window.height, BufferedImage.TYPE_INT_ARGB)
//
//
//        // Copy pixels to image with Y-flip
//        for (y in 0 until height) {
//            for (x in 0 until width) {
//                val i = (y * width + x) * 4
//                val r = bytes[i].toInt() and 0xFF
//                val g = bytes[i + 1].toInt() and 0xFF
//                val b = bytes[i + 2].toInt() and 0xFF
//                val a = bytes[i + 3].toInt() and 0xFF
//
//                // RGBA to ARGB conversion + Y-flip
//                val color = (a shl 24) or (r shl 16) or (g shl 8) or b
//                image.setRGB(x, height - y - 1, color)
//            }
//        }
//
//        // Save image to file
//        try {
//            File("compose-renders").mkdirs()
//            val file = java.io.File("compose-renders/compose-render-${renderI++}.png")
//            if (file.exists()) file.delete()
//            javax.imageio.ImageIO.write(image, "PNG", file)
//            println("Screenshot saved to ${file.absolutePath}")
//        } catch (e: java.io.IOException) {
//            System.err.println("Failed to save screenshot: ${e.message}")
//        }
//    }

//    private var renderI = 0


    fun resize(width: Int, height: Int) = glDebugGroup(500, groupName = { "Skia Resize" }) {
        scene.size = IntSize(width, height)
        window.close()
        window = GlInitFixedSizeComposeWindow(width, height, context)
        invalid = true
    }

    fun close() {
//        glDeleteFramebuffers(frameBuffer)
        scene.close()
        window.close()
//        glBindFramebuffer(GL_FRAMEBUFFER, 0)
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

private fun normalTextureConfig() {
    // Don't care about downscaling, round to nearest actual value
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    // Don't care about upscaling, round to nearest actual value
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
    // In case of numerical errors, define that if a sampling (when checking the depth value to filter out previous layers or just getting the color)
    // flows outside of bounds it will just be clamped to the edge
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
}