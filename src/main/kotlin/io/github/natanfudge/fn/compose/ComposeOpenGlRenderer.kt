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
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunContextRegistry
import io.github.natanfudge.fn.core.InvalidationKey
import io.github.natanfudge.fn.core.SimpleLogger
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.core.valid
import io.github.natanfudge.fn.window.GlfwWindowHolder
import io.github.natanfudge.fn.window.WindowConfig
import io.github.natanfudge.fn.window.WindowHandle
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
import kotlin.coroutines.CoroutineContext


@OptIn(InternalComposeUiApi::class)
class FixedSizeComposeWindow(
    val size: IntSize,
    val sceneWrapper: GlfwComposeScene,
) : InvalidationKey() {

    init {
        GLFW.glfwMakeContextCurrent(sceneWrapper.handle)
        GL.setCapabilities(sceneWrapper.capabilities)

        GLFW.glfwSetWindowSize(sceneWrapper.handle, size.width, size.height)
        sceneWrapper.scene.size = size
        sceneWrapper.sceneContext.platformContext.windowInfo.containerSize = size
    }

    /** Skia surface bound to the current OpenGL FBO. */
    private val surface = glDebugGroup(0, groupName = { "Compose Surface Init" }) {
        createSurface(size, sceneWrapper.context)
    }
    val canvas: org.jetbrains.skia.Canvas = surface.canvas
    private val composeCanvas = canvas.asComposeCanvas()

    private val frameBytes = size.width * size.height * 4
    private val jvmHeapFramebuffer = ByteArray(frameBytes)
    private val offHeapFrameBuffer = MemoryUtil.memAlloc(frameBytes)


    /* Draw the root scene + every overlay layer. */
    fun renderSkia() {
        check(valid)
        check(sceneWrapper.valid)
        // Clear first – Compose does *not* do this every frame.
        glClearColor(0f, 0f, 0f, 0f)
        glDebugGroup(2, groupName = { "${sceneWrapper.label} Canvas Clear" }) {
            canvas.clear(Color.TRANSPARENT)
        }

        val now = System.nanoTime()

        /* 1️⃣  root scene */
        glDebugGroup(3, groupName = { "${sceneWrapper.label} Root Render" }) {
            sceneWrapper.scene.render(composeCanvas, now)
        }

        /* 2️⃣  overlay layers (pop‑ups, tooltips, etc.) */
        glDebugGroup(3, groupName = { "${sceneWrapper.label} Overlays Render" }) {
            sceneWrapper.overlayLayers.forEach { it.renderOn(canvas, now) }
        }

        /* 3️⃣  flush to GL */
        glDebugGroup(4, groupName = { "${sceneWrapper.label} Flush" }) {
            sceneWrapper.context.flush()
        }
    }

    /* Copy back‑buffer into [frame] & emit it. */
    fun blitFrame(): ComposeFrameEvent {
        // Reset buffer position before OpenGL writes to it
        offHeapFrameBuffer.rewind()

        // Copy from GPU to off-heap buffer
        glReadPixels(0, 0, size.width, size.height, GL_RGBA, GL_UNSIGNED_BYTE, offHeapFrameBuffer)

        // Reset position again before reading
        offHeapFrameBuffer.rewind()

        // Copy from off-heap buffer to JVM array
        offHeapFrameBuffer.get(jvmHeapFramebuffer)

        return ComposeFrameEvent(jvmHeapFramebuffer, size)

//        sceneWrapper.frameStream.emit()
    }

    override fun close() {
        surface.close()
        MemoryUtil.memFree(offHeapFrameBuffer)
    }

}

/* ────────────────────────────────────────────────────────────────────────── */
/*  🔸 COMPOSE‑SCENE CONTEXT (GLFW)                                          */
/* ────────────────────────────────────────────────────────────────────────── */

class GlfwComposeSceneContext(
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

class GlfwComposePlatformContext(
    private val onSetPointerIcon: (PointerIcon) -> Unit,
) : PlatformContext by PlatformContext.Empty {
    override val windowInfo = WindowInfoImpl().apply {
        isWindowFocused = true
    }

    override fun setPointerIcon(pointerIcon: PointerIcon) = onSetPointerIcon(pointerIcon)
}

class WindowInfoImpl : WindowInfo {
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
class GlfwComposeScene(
    initialSize: IntSize,
    val handle: WindowHandle,
    onSetPointerIcon: (PointerIcon) -> Unit,
    private val density: Density,
    val getWindowSize: () -> IntSize,
    val label: String,
    val coroutineContext: CoroutineContext,
//    val config: ComposeOpenGLRenderer,
    onError: (Throwable) -> Unit,
    /** Marks this window invalid – render next frame. */
    private val onInvalidate: (GlfwComposeScene) -> Unit,
    val capabilities: GLCapabilities,
) : InvalidationKey() {
    val context = DirectContext.makeGL()

    var frameInvalid = true

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        SimpleLogger.error("ComposeError"){"Compose async error – restarting window"}
        throwable.printStackTrace()
        onError(throwable)
    }
    private val frameDispatcher = FrameDispatcher(coroutineContext) {
        /* Called by Compose when *anything* invalidates. */
        onInvalidate(this)
    }

    val sceneContext = GlfwComposeSceneContext(
        onSetPointerIcon,
        frameDispatcher::scheduleFrame,
        onLayerCreated = {
            overlayLayers.add(it)
            val dim = getWindowSize()
//            if (dim != null) {
            // IDK why, but if you don't set this then Compose doesn't properly initialize the layer. There's no need to resize it or anything
            // on window resize, I think it just needs some initial "push" to compose the initial popup content
            it.boundsInWindow = IntRect(0, 0, dim.width, dim.height)
            it.scene.size = IntSize(dim.width, dim.height)
//            }
        },
        onLayerRemoved = { overlayLayers.remove(it) }
    )

    init {
        sceneContext.platformContext.windowInfo.containerSize = initialSize
    }

    var focused by sceneContext.platformContext.windowInfo::isWindowFocused

    fun sendInputEvent(event: InputEvent) {
        if (event !is InputEvent.WindowClose) {
            // WindowClose invalidates it so it's correct for it to be invalid there
            check(valid)
        }
        if (focused) {
            overlayLayers.asReversed().forEach { it.sendInputEvent(event) }
            scene.sendInputEvent(event)
        }
    }


    /** root scene */
    val scene = PlatformLayersComposeScene(
        coroutineContext = coroutineContext + exceptionHandler,
        density = density,
        invalidate = frameDispatcher::scheduleFrame,
        size = initialSize,
        composeSceneContext = sceneContext
    )

    /** All overlay layers created through the ComposeSceneContext. */
    internal val overlayLayers = mutableListOf<GlfwComposeSceneLayer>()

    fun setContent(content: @Composable () -> Unit) {
        frameInvalid = true
        scene.setContent(content)
    }

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

private fun createSurface(size: IntSize, context: DirectContext): Surface {
    val fbId = glGetInteger(GL_FRAMEBUFFER_BINDING)
    val renderTarget = BackendRenderTarget.makeGL(size.width, size.height, 0, 8, fbId, GR_GL_RGBA8)

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
    val size: IntSize,
)

class ComposeOpenGLRenderer(
    params: WindowConfig,
    val name: String,
    val onSetPointerIcon: (PointerIcon) -> Unit,
    onFrame: (ComposeFrameEvent) -> Unit,
    val onCreateScene: (scene: GlfwComposeScene) -> Unit,
    show: Boolean = false,
    parent: Fun = FunContextRegistry.getContext().rootFun
) : Fun("ComposeOpenGLRenderer-$name", parent) {
    private val offscreenWindow = GlfwWindowHolder(
        withOpenGL = true, showWindow = show, params, name = name,
        onEvent = {} // Ignore events from the offscreen window
    )

    var scene: GlfwComposeScene by cached(offscreenWindow.window) {
        createComposeScene()
    }

    private fun createComposeScene(): GlfwComposeScene {
        val handle = offscreenWindow.window.handle
        GLFW.glfwMakeContextCurrent(handle)
        val size = offscreenWindow.size
        val capabilities = GL.createCapabilities()

        val scene = glDebugGroup(1, groupName = { "$name Compose Init" }) {
            GlfwComposeScene(
                size, handle,
                density = Density(glfwGetWindowContentScale(handle)),
                onSetPointerIcon = onSetPointerIcon,
                label = name,
                coroutineContext = mainThreadCoroutineContext,
                onError = {
                    events.guiError(it)

                    // Refresh compose so it won't die from one exception
                    scene = createComposeScene()
                    canvas = FixedSizeComposeWindow(offscreenWindow.size, scene)
                }, capabilities = capabilities, getWindowSize = {
                    offscreenWindow.size
                }, onInvalidate = {
                    // Invalidate Compose frame on change
                    it.frameInvalid = true
                }
            )
        }
        onCreateScene(scene)
        return scene
    }

    val x = 2

    var canvas by cached(offscreenWindow.window) {
        FixedSizeComposeWindow(offscreenWindow.size, scene)
    }

    fun resize(size: IntSize) {
        if (canvas.size != size) {
            scene.frameInvalid = true
            canvas = FixedSizeComposeWindow(size, scene)
        }
    }

    init {
        events.beforeFrame.listen {
            check(!closed)
            check(scene.valid) {
                "Scene is $scene, i am $this"
            }

            if (scene.frameInvalid) {
                GLFW.glfwMakeContextCurrent(scene.handle)
                GL.setCapabilities(scene.capabilities)

                canvas.renderSkia()
                onFrame(canvas.blitFrame())

                glfwSwapBuffers(scene.handle)
                scene.frameInvalid = false
            }
        }
    }
}


private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}


