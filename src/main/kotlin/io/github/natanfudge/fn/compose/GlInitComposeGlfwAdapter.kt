package io.github.natanfudge.fn.compose


import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.window.subscribeToGLFWEvents
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.skia.*
import org.jetbrains.skia.FramebufferFormat.Companion.GR_GL_RGBA8
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL43.GL_FRAMEBUFFER_BINDING
import java.io.File


//TODO: 2. Hotkey routing (GLFW -> Compose -> User)

@OptIn(InternalComposeUiApi::class)
internal class GlInitComposeGlfwAdapter(
    initialWidth: Int,
    initialHeight: Int,
    windowHandle: Long,
    dispatcher: CoroutineDispatcher,
    private val density: Density,
    private val composeContent: @Composable () -> Unit,
) {
    val drawOffscreen = false

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

        val colorTexture = if (drawOffscreen) createColorTexture(width, height, { "Compose Color Texture" }) {
            normalTextureConfig()
        } else null
        val depthTexture = if (drawOffscreen) createDepthTexture(width, height, { "Compose Depth Texture" }) {
            normalTextureConfig()
        } else null

        init {
            if (colorTexture != null && depthTexture != null) {
                attachColorTextureToFrameBuffer(colorTexture)
                attachDepthTextureToFrameBuffer(depthTexture)
            }
        }

        fun close() {
            surface.close()
            if (colorTexture != null && depthTexture != null) {
                glDeleteTextures(colorTexture)
                glDeleteTextures(depthTexture)
            }
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

    private val composeScene = PlatformLayersComposeScene(
        coroutineContext = dispatcher,
        density = density,
        invalidate = frameDispatcher::scheduleFrame,
        size = IntSize(initialWidth, initialHeight)
    )

    /**
     * Depends on the [window], must be updated when the [window] updates.
     */
    init {
        composeScene.subscribeToGLFWEvents(windowHandle)
        composeScene.setContent {
            composeContent()
        }
    }


    private var frameBuffer: Int = 0

    private fun init(width: Int, height: Int) {
        glDebugGroup(1, groupName = { "Skia Init" }) {
            frameBuffer = createFramebuffer(name = { "Compose Framebuffer" })
            if (drawOffscreen) {
                glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer)
            }
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
        if (drawOffscreen) {
            writeSkiaToImage()
        }
    }


    private fun renderSkia() {
        // Set color explicitly because skia won't reapply it every time
        glClearColor(0f, 0f, 0f, 0f)
        glDebugGroup(2, groupName = { "Compose Canvas Clear" }) {
            window.surface.canvas.clear(Color.TRANSPARENT)
        }

        // Render to the framebuffer
        glDebugGroup(3, groupName = { "Compose Render Content" }) {
            composeScene.render(window.canvas, System.nanoTime())
        }
        glDebugGroup(4, groupName = { "Compose Flush" }) {
            context.flush()
        }
    }

    private fun writeSkiaToImage() {
        val width = window.width
        val height = window.height

        // Allocate buffer for pixel data
        val buffer = java.nio.ByteBuffer.allocateDirect(width * height * 4)
        buffer.order(java.nio.ByteOrder.nativeOrder())

        // Read pixels from framebuffer
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)

        // Create buffered image (note: OpenGL has origin at bottom left, we need to flip it)
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)

        // Copy pixels to image with Y-flip
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = (y * width + x) * 4
                val r = buffer.get(i).toInt() and 0xFF
                val g = buffer.get(i + 1).toInt() and 0xFF
                val b = buffer.get(i + 2).toInt() and 0xFF
                val a = buffer.get(i + 3).toInt() and 0xFF

                // RGBA to ARGB conversion + Y-flip
                val color = (a shl 24) or (r shl 16) or (g shl 8) or b
                image.setRGB(x, height - y - 1, color)
            }
        }

        // Save image to file
        try {
            File("compose-renders").mkdirs()
            val file = java.io.File("compose-renders/compose-render-${renderI++}.png")
            if (file.exists()) file.delete()
            javax.imageio.ImageIO.write(image, "PNG", file)
            println("Screenshot saved to ${file.absolutePath}")
        } catch (e: java.io.IOException) {
            System.err.println("Failed to save screenshot: ${e.message}")
        }
    }

    private var renderI = 0


    fun resize(width: Int, height: Int) = glDebugGroup(500, groupName = { "Skia Resize" }) {
        composeScene.size = IntSize(width, height)
        window.close()
        window = GlInitFixedSizeComposeWindow(width, height, context)
        invalid = true
    }

    fun close() {
        glDeleteFramebuffers(frameBuffer)
        composeScene.close()
        window.close()
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
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