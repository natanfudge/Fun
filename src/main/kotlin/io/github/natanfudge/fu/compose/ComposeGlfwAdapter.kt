package io.github.natanfudge.fu.compose


import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.skia.*
import org.jetbrains.skia.FramebufferFormat.Companion.GR_GL_RGBA8
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL43.GL_FRAMEBUFFER_BINDING

@OptIn(InternalComposeUiApi::class)
private data class FixedSizeComposeWindow(
    val width: Int,
    val height: Int,
    val surface: Surface,
    val canvas: Canvas,
    val colorTexture: Int,
    val depthTexture: Int,
) {
    companion object {
        //TODO: currently we put everything on an offscreen framebuffer. In practice it might be simple or better to use the main framebuffer.
        fun create(width: Int, height: Int, context: DirectContext, framebuffer: Int): FixedSizeComposeWindow {
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
            // Skia Surface, bound to the OpenGL context
            val surface = glDebugGroup(0, groupName = { "Compose Surface Init" }) {
                createSurface(width, height, context)
            }
            val canvas = surface.canvas.asComposeCanvas()

            val colorTexture = createColorTexture(width, height, { "Compose Color Texture" }) {
                normalTextureConfig()
            }
            val depthTexture = createDepthTexture(width, height, { "Compose Depth Texture" }) {
                normalTextureConfig()
            }

            attachColorTextureToFrameBuffer(colorTexture)
            attachDepthTextureToFrameBuffer(depthTexture)

            return FixedSizeComposeWindow(width, height, surface, canvas, colorTexture, depthTexture)
        }
    }

    fun close() {
        surface.close()
        glDeleteTextures(colorTexture)
        glDeleteTextures(depthTexture)
    }
}

//TODO: 1. resize handling
//TODO: 2. Hotkey routing (GLFW -> Compose -> User)

@OptIn(InternalComposeUiApi::class)
internal class ComposeGlfwAdapter private constructor(
    private val context: DirectContext,
    private val dispatcher: CoroutineDispatcher,
    private val density: Density,
) {

    private lateinit var window: FixedSizeComposeWindow

    /**
     * Depends on the [surface], must be updated when the [surface] updates.
     */
    private lateinit var composeScene: ComposeScene


    companion object {
        /**
         * @param dispatcher executes Compose async actions, need to be run in a controlled way to avoid race conditions.
         */
        fun create(
            initialWidth: Int,
            initialHeight: Int,
            dispatcher: CoroutineDispatcher,
            density: Density,
        ): ComposeGlfwAdapter {
            val context = DirectContext.makeGL()
            return ComposeGlfwAdapter(context, dispatcher, density).apply {
                init(initialWidth, initialHeight)
            }
        }
    }


    private var frameBuffer: Int = 0

    private fun init(width: Int, height: Int) {
        glDebugGroup(1, groupName = { "Skia Init" }) {
            frameBuffer = createFramebuffer(name = { "Compose Framebuffer" })
            window = FixedSizeComposeWindow.create(width, height, context, frameBuffer)
        }
    }


    private var invalid = true

    fun draw() = glDebugGroup(5, groupName = { "Compose Render" }) {
        // Every frame - overlay the GUI on top of the framebuffer
//        overlaySkia()
        if (x == 0) {
            try {
                // When updates are needed - render new content
                renderSkia()
                invalid = false
            } catch (e: Throwable) {
                System.err.println("Error during Skia rendering! This is usually a Compose user error.")
                e.printStackTrace()
            }
            writeSkiaToImage()
        }
        x = (x + 1) % 120
    }

    var x = 0

    private fun renderSkia() {
        // Set color explicitly because skia won't reapply it every time
        glClearColor(0f, 0f, 0f, 0f)
        glDebugGroup(2, groupName = { "Compose Canvas Clear" }) {
            window.surface.canvas.clear(Color.TRANSPARENT)
        }

        // Render to the framebuffer
        glDebugGroup(3, groupName = { "Compose Render Content" }) {
            composeScene.render(window.surface.canvas.asComposeCanvas(), System.nanoTime())
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
            val file = java.io.File("compose-render-${renderI++}.png")
            if (file.exists()) file.delete()
            javax.imageio.ImageIO.write(image, "PNG", file)
            println("Screenshot saved to ${file.absolutePath}")
        } catch (e: java.io.IOException) {
            System.err.println("Failed to save screenshot: ${e.message}")
        }
    }

    private var renderI = 0

//    private fun overlaySkia() {
//        program.withBind {
//            glEnable(GL_BLEND);
//            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
//            glActiveTexture(GL_TEXTURE0)
//            withTexture(colorTexture) {
//                textureToDrawUniform.set(0)
//
//                // Draw a triangle covering the entire screen
//                glDrawArrays(GL_TRIANGLES, 0, 3)
//            }
//
//        }
//    }

    fun resize(width: Int, height: Int) = glDebugGroup(500, groupName = { "Skia Resize" }) {
        composeScene.size = IntSize(width, height)
        window.close()
        window = FixedSizeComposeWindow.create(width, height, context, frameBuffer)
    }


    fun setScene(width: Int, height: Int, content: @Composable () -> Unit) {
        val frameDispatcher = FrameDispatcher(dispatcher) {
            // Draw new skia content
            invalid = true
        }

        composeScene = PlatformLayersComposeScene(
            coroutineContext = dispatcher,
            density = density,
            invalidate = frameDispatcher::scheduleFrame,
            size = IntSize(width, height)
        )

        composeScene.setContent {
            content()
        }
    }


    fun close() {
        glDeleteFramebuffers(frameBuffer)
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
//
//private fun glfwGetWindowContentScale(window: Long): Float {
//    val array = FloatArray(1)
//    GLFW.glfwGetWindowContentScale(window, array, FloatArray(1))
//    return array[0]
//}

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