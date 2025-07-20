//@file:OptIn(InternalComposeUiApi::class)
//
//package io.github.natanfudge.fn.compose
//
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.CompositionContext
//import androidx.compose.runtime.CompositionLocalContext
//import androidx.compose.ui.InternalComposeUiApi
//import androidx.compose.ui.graphics.asComposeCanvas
//import androidx.compose.ui.input.key.KeyEvent
//import androidx.compose.ui.input.pointer.PointerButton
//import androidx.compose.ui.input.pointer.PointerEventType
//import androidx.compose.ui.input.pointer.PointerIcon
//import androidx.compose.ui.platform.PlatformContext
//import androidx.compose.ui.scene.ComposeSceneContext
//import androidx.compose.ui.scene.ComposeSceneLayer
//import androidx.compose.ui.scene.PlatformLayersComposeScene
//import androidx.compose.ui.unit.*
//import io.github.natanfudge.fn.core.ProcessLifecycle
//import io.github.natanfudge.fn.util.EventEmitter
//import io.github.natanfudge.fn.util.EventStream
//import io.github.natanfudge.fn.util.Lifecycle
//import io.github.natanfudge.fn.window.*
//import kotlinx.coroutines.CoroutineExceptionHandler
//import org.jetbrains.skia.*
//import org.jetbrains.skia.FramebufferFormat.Companion.GR_GL_RGBA8
//import org.jetbrains.skiko.FrameDispatcher
//import org.lwjgl.glfw.GLFW
//import org.lwjgl.glfw.GLFW.glfwGetWindowContentScale
//import org.lwjgl.glfw.GLFW.glfwSwapBuffers
//import org.lwjgl.opengl.GL
//import org.lwjgl.opengl.GL11.*
//import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING
//import org.lwjgl.opengl.GLCapabilities
//import org.lwjgl.system.MemoryUtil
//import java.nio.ByteBuffer
//import kotlin.time.Duration
//
//
//@OptIn(InternalComposeUiApi::class)
//class FixedSizeComposeWindow(
//    val width: Int,
//    val height: Int,
//    val window: ComposeGlfwWindow,
////    context: DirectContext,
//) : AutoCloseable {
//    init {
//        GLFW.glfwMakeContextCurrent(window.handle)
//        GL.setCapabilities(window.capabilities)
//    }
//
//    // Skia Surface, bound to the OpenGL context
//    val surface = glDebugGroup(0, groupName = { "Compose Surface Init" }) {
//        createSurface(width, height, window.context)
//    }
//    val canvas = surface.canvas.asComposeCanvas()
//
//    val frame = ByteArray(width * height * 4)
//    val frameByteBuffer = MemoryUtil.memAlloc(width * height * 4)
//
////    init {
////        window.invalid = true
////    }
//
//    override fun toString(): String {
//        return "Compose Window w=$width, h=$height"
//    }
//
//    override fun close() {
//        surface.close()
//        MemoryUtil.memFree(frameByteBuffer)
//    }
//}
//
//class GlfwComposeSceneContext(onSetPointerIcon: (PointerIcon) -> Unit) : ComposeSceneContext {
//    override val platformContext = GlfwComposePlatformContext(onSetPointerIcon)
//    override fun createLayer(
//        density: Density,
//        layoutDirection: LayoutDirection,
//        focusable: Boolean,
//        compositionContext: CompositionContext,
//    ): ComposeSceneLayer {
//        return GlfwComposeSceneLayer(density, layoutDirection, focusable, compositionContext)
//    }
//}
//
//@OptIn(InternalComposeUiApi::class)
//class GlfwComposeSceneLayer(
//    initialDensity: Density,
//    initialLayoutDirection: LayoutDirection,
//    override var focusable: Boolean,
//    private val compositionContext: CompositionContext,
//    /** Propagate Compose invalidations up to the owning window. */
//    private val onInvalidate: () -> Unit = {},
//) : ComposeSceneLayer {
//
//    /* ── public mutable props ─────────────────────────────────────────────── */
//
//    override var density: Density = initialDensity
//        set(value) {
//            field = value
//            scene.density = value           // keep inner scene in‑sync
//        }
//
//    override var layoutDirection: LayoutDirection = initialLayoutDirection
//        set(value) {
//            field = value
//            scene.layoutDirection = value    // keep inner scene in‑sync
//        }
//
//    /* ── private backing scene ────────────────────────────────────────────── */
//
//    private val scene = PlatformLayersComposeScene(
//        coroutineContext = compositionContext.effectCoroutineContext,
//        density = density,
//        layoutDirection = layoutDirection,
//        invalidate = onInvalidate,
//        composeSceneContext = GlfwComposeSceneContext {
//            // TODO
//            /* no‑op icon setter */
//        }
//    )
//
//    /* ── ComposeSceneLayer contract ───────────────────────────────────────── */
//
//    override var boundsInWindow: IntRect = IntRect.Zero
//    override var compositionLocalContext: CompositionLocalContext? by scene::compositionLocalContext
//    override var scrimColor: androidx.compose.ui.graphics.Color? = null
//
//    override fun close() = scene.close()
//
//    override fun setContent(content: @Composable () -> Unit) =
//        scene.setContent(content)
//
//    override fun setKeyEventListener(
//        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
//        onKeyEvent: ((KeyEvent) -> Boolean)?,
//    ) {
//        //TODO
//    }
//
//    override fun setOutsidePointerEventListener(
//        onOutsidePointerEvent: ((PointerEventType, PointerButton?) -> Unit)?,
//    ) {
//        //TODO
//    }
//
//    /** Translate a point from window coordinates to this layer’s local coordinates. */
//    override fun calculateLocalPosition(positionInWindow: IntOffset): IntOffset =
//        IntOffset(
//            positionInWindow.x - boundsInWindow.left,
//            positionInWindow.y - boundsInWindow.top
//        )
//}
//
//class GlfwComposePlatformContext(
//    private val onSetPointerIcon: (PointerIcon) -> Unit,
//) : PlatformContext by PlatformContext.Empty {
//    override fun setPointerIcon(pointerIcon: PointerIcon) {
//        this.onSetPointerIcon(pointerIcon)
//    }
//}
//
//@OptIn(InternalComposeUiApi::class)
//class ComposeGlfwWindow(
//    initialWidth: Int,
//    initialHeight: Int,
//    // Background window used for OpenGL rendering
//    val handle: WindowHandle,
//    onSetPointerIcon: (PointerIcon) -> Unit,
//    private val density: Density,
////    private val composeContent: @Composable () -> Unit,
//    val config: ComposeOpenGLRenderer,
//    private val onError: (Throwable) -> Unit,
//    private val onInvalidate: () -> Unit,
//    val capabilities: GLCapabilities,
//) : AutoCloseable {
//
//
//    val frameStream = EventEmitter<ComposeFrameEvent>()
//    val context = DirectContext.makeGL()
//    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
//        System.err.println("An error occurred inside an asynchronous Compose callback. The GUI will restart itself to recover.")
//        throwable.printStackTrace()
//        config.windowLifecycle.restart()
//        onError(throwable)
//    }
//    val dispatcher = GlfwCoroutineDispatcher()
//    val frameDispatcher = FrameDispatcher(dispatcher) {
//        // Draw new skia content
//        onInvalidate()
//    }
//    var invalid = true
//
//    var focused = true
//
//    val glfwContext = GlfwComposeSceneContext(onSetPointerIcon)
//
//    val scene = PlatformLayersComposeScene(
//        coroutineContext = dispatcher + exceptionHandler,
//        density = density,
//        invalidate = frameDispatcher::scheduleFrame,
//        size = IntSize(initialWidth, initialHeight),
//        composeSceneContext = glfwContext
//    )
//
//    fun setContent(content: @Composable () -> Unit) {
//        scene.setContent(content)
//    }
//
//    override fun close() {
//        scene.close()
//    }
//}
//
//private fun createSurface(width: Int, height: Int, context: DirectContext): Surface {
//    val fbId = glGetInteger(GL_FRAMEBUFFER_BINDING)
//    val renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, fbId, GR_GL_RGBA8)
//
//    return Surface.makeFromBackendRenderTarget(
//        context, renderTarget, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.sRGB
//    )!!
//}
//
//data class ComposeFrameEvent(
//    val bytes: ByteArray,
//    val width: Int,
//    val height: Int,
//)
//
//class ComposeOpenGLRenderer(
//    windowParameters: WindowParameters,
//    windowDimensionsLifecycle: Lifecycle<WindowDimensions>,
//    beforeFrameEvent: EventStream<Duration>,
//    private val name: String,
////    val content: @Composable () -> Unit = { Text("Hello!") },
//    onSetPointerIcon: (PointerIcon) -> Unit,
//    onError: (Throwable) -> Unit,
//    show: Boolean = false,
//    parentLifecycle: Lifecycle<Unit> = ProcessLifecycle,
//) {
//
//    val LifecycleLabel = "$name Compose Window"
//
//    val glfw = GlfwWindowConfig(
//        GlfwConfig(disableApi = false, showWindow = show), name = "Compose $name", windowParameters.copy(
//            initialTitle = name
//        ), parentLifecycle
//    )
//
//    // We want this one to start early so we can update its size with the dimensions lifecycle afterwards
//    val windowLifecycle: Lifecycle<ComposeGlfwWindow> = glfw.windowLifecycle.bind(LifecycleLabel, early = true) {
//        GLFW.glfwMakeContextCurrent(it.handle)
//        val capabilities = GL.createCapabilities()
//
//        glDebugGroup(1, groupName = { "$name Compose Init" }) {
//            var window: ComposeGlfwWindow? = null
//            window = ComposeGlfwWindow(
//                it.init.initialWindowWidth, it.init.initialWindowHeight, it.handle,
//                density = Density(glfwGetWindowContentScale(it.handle)),
//                onSetPointerIcon = onSetPointerIcon,
//                config = this@ComposeOpenGLRenderer, onError = onError, capabilities = capabilities, onInvalidate = {
//                    // Invalidate Compose frame on change
//                    window!!.invalid = true
//                }
//            )
//            window
//        }
//    }
//
//
//    val dimensionsLifecycle: Lifecycle<FixedSizeComposeWindow> =
//        windowDimensionsLifecycle.bind(windowLifecycle, "$name Compose Fixed Size Window") { dim, window ->
//            GLFW.glfwSetWindowSize(window.handle, dim.width, dim.height)
//            window.scene.size = IntSize(dim.width, dim.height)
//
//            FixedSizeComposeWindow(dim.width, dim.height, window)
//        }
//
//    init {
//        //TODO: this needs to get unregistered properly once we consolidate rendering API into the Fun API (this would be a Fun and we can just call listen {})
//        beforeFrameEvent.listenUnscoped {
//            val dim = dimensionsLifecycle.assertValue
//            val window = dim.window
//            window.dispatcher.poll()
//            if (window.invalid) {
//                GLFW.glfwMakeContextCurrent(window.handle)
//                GL.setCapabilities(window.capabilities)
//                dim.draw()
//                glfwSwapBuffers(window.handle)
//                window.invalid = false
//            }
//        }
////        // Make sure we get the frame early so we can draw it in the webgpu pass of the current frame
////        // Also we need
////        host.frameLifecycle.bind(dimensionsLifecycle, "$name Compose Frame Store", FunLogLevel.Verbose, early1 = true) { delta, dim ->
////
////        }
//
//    }
//
//
//    private fun FixedSizeComposeWindow.draw() {
//        glDebugGroup(5, groupName = { "$name Compose Render" }) {
//            try {
//                // When updates are needed - render new content
//                renderSkia()
//            } catch (e: Throwable) {
//                System.err.println("Error during Skia rendering of $name! This is usually a Compose user error.")
//                e.printStackTrace()
//            }
//
//            // SLOW: This method of copying the frame into ByteArrays and then drawing them as a texture is extremely slow, but there
//            // is probably no better alternative at the moment. We need some way to draw skia into a WebGPU context.
//            // For that we need:
//            // A. Skiko vulkan support
//            // B. Skiko graphite support for WebGPU support
//            // C. Compose skiko vulkan / webgpu / metal support
//            // D. Integrate the rendering with each rendering api separately - we need to 'fetch' the vulkan context in our main webgpu app, and draw compose on top of it, and same for the other APIs.
//
//            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, frameByteBuffer)
//            val buffer = getFrameBytes()
//            buffer.get(frame)
//
//            window.frameStream.emit(ComposeFrameEvent(frame, width, height))
//        }
//    }
//
//
//    private fun FixedSizeComposeWindow.renderSkia() {
//        // Set color explicitly because skia won't reapply it every time
//        glClearColor(0f, 0f, 0f, 0f)
//        glDebugGroup(2, groupName = { "$name Compose Canvas Clear" }) {
//            surface.canvas.clear(Color.TRANSPARENT)
//        }
//
//        // Render to the framebuffer
//        glDebugGroup(3, groupName = { "$name Compose Render Content" }) {
//            window.scene.render(canvas, System.nanoTime())
//        }
//        glDebugGroup(4, groupName = { "$name Compose Flush" }) {
//            window.context.flush()
//        }
//    }
//
//    private fun FixedSizeComposeWindow.getFrameBytes(): ByteBuffer {
//        val buffer = MemoryUtil.memAlloc(width * height * 4)
//        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
//        return buffer
//    }
//
//
//}
//
//
//private fun glfwGetWindowContentScale(window: Long): Float {
//    val array = FloatArray(1)
//    glfwGetWindowContentScale(window, array, FloatArray(1))
//    return array[0]
//}