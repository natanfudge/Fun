@file:OptIn(InternalComposeUiApi::class)

package io.github.natanfudge.fn.compose

import androidx.compose.runtime.*
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.ComposeSceneLayer
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.*
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.window.*
import kotlinx.coroutines.CoroutineExceptionHandler
import org.jetbrains.skia.*
import org.jetbrains.skia.FramebufferFormat.Companion.GR_GL_RGBA8
import org.jetbrains.skiko.FrameDispatcher
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwGetWindowContentScale
import org.lwjgl.glfw.GLFW.glfwSwapBuffers
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.system.MemoryUtil
import kotlin.time.Duration


/* ────────────────────────────────────────────────────────────────────────── */
/*  🔸 FIXED‑SIZE OFF‑SCREEN COMPOSE WINDOW                                  */
/* ────────────────────────────────────────────────────────────────────────── */

@OptIn(InternalComposeUiApi::class)
internal class FixedSizeComposeWindow(
    val width: Int,
    val height: Int,
    val window: ComposeGlfwWindow,
) : AutoCloseable {

    init {
        GLFW.glfwMakeContextCurrent(window.handle)
        GL.setCapabilities(window.capabilities)
    }

    /** Skia surface bound to the current OpenGL FBO. */
    private val surface = glDebugGroup(0, groupName = { "Compose Surface Init" }) {
        createSurface(width, height, window.context)
    }
    val canvas: org.jetbrains.skia.Canvas = surface.canvas
    private val composeCanvas = canvas.asComposeCanvas()

    private val frameBytes = width * height * 4
    private val jvmHeapFramebuffer = ByteArray(frameBytes)
    private val offHeapFrameBuffer = MemoryUtil.memAlloc(frameBytes)



    /* Draw the root scene + every overlay layer. */
    fun renderSkia() {
        // Clear first – Compose does *not* do this every frame.
        glClearColor(0f, 0f, 0f, 0f)
        glDebugGroup(2, groupName = { "${window.config.name} Canvas Clear" }) {
            canvas.clear(Color.TRANSPARENT)
        }

        val now = System.nanoTime()

        /* 1️⃣  root scene */
        glDebugGroup(3, groupName = { "${window.config.name} Root Render" }) {
            window.scene.render(composeCanvas, now)
        }

        /* 2️⃣  overlay layers (pop‑ups, tooltips, etc.) */
        glDebugGroup(3, groupName = { "${window.config.name} Overlays Render" }) {
            window.overlayLayers.forEach { it.renderOn(canvas, now) }
        }

        /* 3️⃣  flush to GL */
        glDebugGroup(4, groupName = { "${window.config.name} Flush" }) {
            window.context.flush()
        }
    }

    /* Copy back‑buffer into [frame] & emit it. */
    fun blitFrame() {
        // Reset buffer position before OpenGL writes to it
        offHeapFrameBuffer.rewind()

        // Copy from GPU to off-heap buffer
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, offHeapFrameBuffer)

        // Reset position again before reading
        offHeapFrameBuffer.rewind()

        // Copy from off-heap buffer to JVM array
        offHeapFrameBuffer.get(jvmHeapFramebuffer)


        window.frameStream.emit(ComposeFrameEvent(jvmHeapFramebuffer, width, height))
    }

    override fun close() {
        surface.close()
        MemoryUtil.memFree(offHeapFrameBuffer)
    }

}

/* ────────────────────────────────────────────────────────────────────────── */
/*  🔸 COMPOSE‑SCENE CONTEXT (GLFW)                                          */
/* ────────────────────────────────────────────────────────────────────────── */

internal class GlfwComposeSceneContext(
    private val onSetPointerIcon: (PointerIcon) -> Unit,
    private val onInvalidate: () -> Unit,
    private val onLayerCreated: (GlfwComposeSceneLayer) -> Unit,
    private val onLayerRemoved: (GlfwComposeSceneLayer) -> Unit,
) : ComposeSceneContext {

    override val platformContext = GlfwComposePlatformContext(onSetPointerIcon)

    override fun createLayer(
        density: Density,
        layoutDirection: LayoutDirection,
        focusable: Boolean,
        compositionContext: CompositionContext,
    ): ComposeSceneLayer {
        val layer = GlfwComposeSceneLayer(
            density,
            layoutDirection,
            focusable,
            compositionContext,
            onInvalidate,
            onLayerRemoved,
            parentContext = this
        )
        onLayerCreated(layer)
        return layer
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  🔸 COMPOSE LAYER IMPLEMENTATION (GLFW)                                   */
/* ────────────────────────────────────────────────────────────────────────── */

@OptIn(InternalComposeUiApi::class)
class GlfwComposeSceneLayer(
    initialDensity: Density,
    initialLayoutDirection: LayoutDirection,
    override var focusable: Boolean,
    private val compositionContext: CompositionContext,
    /** Schedules a new frame on the owning window. */
    private val onInvalidate: () -> Unit,
    private val onClose: (GlfwComposeSceneLayer) -> Unit,
    private val parentContext: ComposeSceneContext,
) : ComposeSceneLayer {

    /*  public mutable props  */
    override var density: Density = initialDensity
        set(value) {
            field = value
            scene.density = value
        }

    override var layoutDirection: LayoutDirection = initialLayoutDirection
        set(value) {
            field = value
            scene.layoutDirection = value
        }

    /*  private backing scene  */
    val scene = PlatformLayersComposeScene(
        coroutineContext = compositionContext.effectCoroutineContext,
        density = density,
        layoutDirection = layoutDirection,
        invalidate = onInvalidate,
        composeSceneContext = parentContext
    )

    fun sendInputEvent(event: InputEvent) {
        if (event is InputEvent.PointerEvent && event.position !in boundsInWindow) {
            // Clicked outside - invoke onOutsidePointer, not the normal callback.
            onOutsidePointer?.invoke(event.eventType, event.button)
        } else {
            scene.sendInputEvent(event.offset(-boundsInWindow.topLeft))
        }

    }

    /*  listener storage  */
    // SUS: currently not handling this one and just shoving input events into the ComposeScene. not sure why these 2 are needed
    var onPreviewKey: ((KeyEvent) -> Boolean)? = null
    var onKey: ((KeyEvent) -> Boolean)? = null


    var onOutsidePointer:
            ((PointerEventType, PointerButton?) -> Unit)? = null

    /*  ComposeSceneLayer contract  */
    // Important comment from the original Desktop swing implementation:
    // "    // It shouldn't be used for setting canvas size - it will crop drawings outside"
    // So we update the canvas size seperately.
    // Note that currently it gets the same values as the canvas size, but it's supposed to be more precise.
    override var boundsInWindow: IntRect = IntRect.Zero
    override var compositionLocalContext: CompositionLocalContext? by scene::compositionLocalContext
    override var scrimColor: androidx.compose.ui.graphics.Color? = null

    override fun close() {
        scene.close()
        onClose(this)
    }

    override fun setContent(content: @Composable () -> Unit) =
        scene.setContent(content)

    override fun setKeyEventListener(
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
    ) {
        onPreviewKey = onPreviewKeyEvent
        onKey = onKeyEvent
    }

    override fun setOutsidePointerEventListener(
        onOutsidePointerEvent: ((PointerEventType, PointerButton?) -> Unit)?,
    ) {
        onOutsidePointer = onOutsidePointerEvent
    }

    /** Convert window coords → local coords. */
    override fun calculateLocalPosition(positionInWindow: IntOffset): IntOffset =
        IntOffset(
            positionInWindow.x - boundsInWindow.left,
            positionInWindow.y - boundsInWindow.top,
        )

    /* ──  DRAWING  ─────────────────────────────────────────────────────── */

    fun renderOn(canvas: Canvas, nanoTime: Long) {
        if (boundsInWindow.isEmpty) return

        canvas.save()
        canvas.clipRect(
            Rect(
                boundsInWindow.left.toFloat(),
                boundsInWindow.top.toFloat(),
                boundsInWindow.right.toFloat(),
                boundsInWindow.bottom.toFloat(),
            )
        )
        canvas.translate(boundsInWindow.left.toFloat(), boundsInWindow.top.toFloat())
        scene.render(canvas.asComposeCanvas(), nanoTime)
        canvas.restore()
    }
}

operator fun IntRect.contains(offset: Offset) = offset.x >= left && offset.x <= right && offset.y <= bottom && offset.y >= top



/* ────────────────────────────────────────────────────────────────────────── */
/*  🔸 GLFW PLATFORM CONTEXT (POINTER ICON ONLY)                             */
/* ────────────────────────────────────────────────────────────────────────── */

internal class GlfwComposePlatformContext(
    private val onSetPointerIcon: (PointerIcon) -> Unit,
) : PlatformContext by PlatformContext.Empty {
    override val windowInfo = WindowInfoImpl().apply {
        isWindowFocused = true
    }

    override fun setPointerIcon(pointerIcon: PointerIcon) = onSetPointerIcon(pointerIcon)
}

internal class WindowInfoImpl : WindowInfo {
    private val _containerSize = mutableStateOf(IntSize.Zero)

    override var isWindowFocused: Boolean by mutableStateOf(false)

    override var keyboardModifiers: PointerKeyboardModifiers
        get() = GlobalKeyboardModifiers.value
        set(value) {
            GlobalKeyboardModifiers.value = value
        }

    override var containerSize: IntSize
        get() = _containerSize.value
        set(value) {
            _containerSize.value = value
        }

    companion object {
        // One instance across all windows makes sense, since the state of KeyboardModifiers is
        // common for all windows.
        internal val GlobalKeyboardModifiers = mutableStateOf(PointerKeyboardModifiers())
    }
}


/* ────────────────────────────────────────────────────────────────────────── */
/*  🔸 COMPOSE WINDOW (GLFW)                                                 */
/* ────────────────────────────────────────────────────────────────────────── */

@OptIn(InternalComposeUiApi::class)
internal class ComposeGlfwWindow(
    initialWidth: Int,
    initialHeight: Int,
    val handle: WindowHandle,
    onSetPointerIcon: (PointerIcon) -> Unit,
    private val density: Density,
    val config: ComposeOpenGLRenderer,
    onError: (Throwable) -> Unit,
    /** Marks this window invalid – render next frame. */
    private val onInvalidate: () -> Unit,
    val capabilities: GLCapabilities,
) : AutoCloseable {

    val frameStream = EventEmitter<ComposeFrameEvent>()
    val context = DirectContext.makeGL()
    var invalid = true

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        System.err.println("Compose async error – restarting window")
        throwable.printStackTrace()
        config.windowLifecycle.restart()
        onError(throwable)
    }

    val dispatcher = GlfwCoroutineDispatcher()
    private val frameDispatcher = FrameDispatcher(dispatcher) {
        /* Called by Compose when *anything* invalidates. */
        onInvalidate()
    }

    val sceneContext = GlfwComposeSceneContext(
        onSetPointerIcon,
        frameDispatcher::scheduleFrame,
        onLayerCreated = {
            overlayLayers.add(it)
            val dim = config.dimensionsLifecycle.value
            if (dim != null) {
                // IDK why, but if you don't set this then Compose doesn't properly initialize the layer. There's no need to resize it or anything
                // on window resize, I think it just needs some initial "push" to compose the initial popup content
                it.boundsInWindow = IntRect(0, 0, dim.width, dim.height)
                it.scene.size = IntSize(dim.width, dim.height)
            }
        },
        onLayerRemoved = { overlayLayers.remove(it) }
    )

    init {
        sceneContext.platformContext.windowInfo.containerSize = IntSize(initialWidth, initialHeight)
    }

    var focused by sceneContext.platformContext.windowInfo::isWindowFocused

    fun sendInputEvent(event: InputEvent) {
        if (focused) {
            overlayLayers.asReversed().forEach { it.sendInputEvent(event) }
            scene.sendInputEvent(event)

//            overlayLayers.asReversed().forEach { it.onPreviewKey?.invoke(key) }
//            overlayLayers.asReversed().forEach { it.onKey?.invoke(key) }
//            it.onOutsidePointer?.invoke(eventType, button)


        }
    }


    /** root scene */
    val scene = PlatformLayersComposeScene(
        coroutineContext = dispatcher + exceptionHandler,
        density = density,
        invalidate = frameDispatcher::scheduleFrame,
        size = IntSize(initialWidth, initialHeight),
        composeSceneContext = sceneContext
    )

    /** All overlay layers created through the ComposeSceneContext. */
    internal val overlayLayers = mutableListOf<GlfwComposeSceneLayer>()

    fun setContent(content: @Composable () -> Unit) = scene.setContent(content)

    override fun close() = scene.close()
}

/**
 * Offset mouse events by a given amount, to give compose layers a position relative to their own coordinate system (their root is often not at (0,0))
 */
private fun InputEvent.offset(offset: IntOffset) = when (this) {
    is InputEvent.PointerEvent -> {
        copy(position = position + offset)
    }

    else -> this
}

private fun ComposeScene.sendInputEvent(event: InputEvent) {
    when (event) {
        is InputEvent.KeyEvent -> {
            val key = event.event
            sendKeyEvent(key)
        }

        is InputEvent.PointerEvent -> {
            with(event) {
                sendPointerEvent(
                    eventType,
                    position,
                    scrollDelta,
                    timeMillis,
                    type,
                    buttons,
                    keyboardModifiers,
                    nativeEvent,
                    button
                )
            }
        }

        else -> {}
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  🔸 OPENGL RENDERER                                                       */
/* ────────────────────────────────────────────────────────────────────────── */

private fun createSurface(width: Int, height: Int, context: DirectContext): Surface {
    val fbId = glGetInteger(GL_FRAMEBUFFER_BINDING)
    val renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, fbId, GR_GL_RGBA8)

    return Surface.makeFromBackendRenderTarget(
        context,
        renderTarget,
        SurfaceOrigin.BOTTOM_LEFT,
        SurfaceColorFormat.RGBA_8888,
        ColorSpace.sRGB,
    )!!
}

data class ComposeFrameEvent(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)


internal class ComposeOpenGLRenderer(
    windowParameters: WindowConfig,
    windowDimensionsLifecycle: Lifecycle<WindowDimensions>,
    beforeFrameEvent: EventStream<Duration>,
    val name: String,
    onSetPointerIcon: (PointerIcon) -> Unit,
    onError: (Throwable) -> Unit,
    show: Boolean = false,
    parentLifecycle: Lifecycle<Unit> = ProcessLifecycle,
) {

    val LifecycleLabel = "$name Compose Window"

    private val glfw = GlfwWindowConfig(
        GlfwConfig(disableApi = false, showWindow = show),
        name = "Compose $name",
        windowParameters.copy(initialTitle = name),
        parentLifecycle,
    )


    val windowLifecycle: Lifecycle<ComposeGlfwWindow> = glfw.windowLifecycle.bind(
        LifecycleLabel,
        early = true,
    ) {
        GLFW.glfwMakeContextCurrent(it.handle)
        val capabilities = GL.createCapabilities()

        glDebugGroup(1, groupName = { "$name Compose Init" }) {
            var window: ComposeGlfwWindow? = null
            window = ComposeGlfwWindow(
                it.params.initialWindowWidth, it.params.initialWindowHeight, it.handle,
                density = Density(glfwGetWindowContentScale(it.handle)),
                onSetPointerIcon = onSetPointerIcon,
                config = this@ComposeOpenGLRenderer, onError = onError, capabilities = capabilities, onInvalidate = {
                    // Invalidate Compose frame on change
                    window!!.invalid = true
                }
            )
            window
        }
    }


    val dimensionsLifecycle: Lifecycle<FixedSizeComposeWindow> =
        windowDimensionsLifecycle.bind(windowLifecycle, "$name Fixed Size") { dim, win ->
            GLFW.glfwSetWindowSize(win.handle, dim.width, dim.height)
            val size = IntSize(dim.width, dim.height)
            win.scene.size = size
            win.sceneContext.platformContext.windowInfo.containerSize = size
            FixedSizeComposeWindow(dim.width, dim.height, win)
        }

    init {
        beforeFrameEvent.listenUnscoped {
            val dim = dimensionsLifecycle.assertValue
            val window = dim.window

            window.dispatcher.poll()

            if (window.invalid) {
                GLFW.glfwMakeContextCurrent(window.handle)
                GL.setCapabilities(window.capabilities)

                dim.renderSkia()
                dim.blitFrame()

                glfwSwapBuffers(window.handle)
                window.invalid = false
            }
        }
    }
}

/* ────────────────────────────────────────────────────────────────────────── */
/*  🔸 HELPERS                                                               */
/* ────────────────────────────────────────────────────────────────────────── */

private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}
