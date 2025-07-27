@file:OptIn(InternalComposeUiApi::class)

package io.github.natanfudge.fn.compose

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import io.github.natanfudge.fn.compose.ComposeHudWebGPURenderer.ComposeBindGroup
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.fn.util.ValueHolder
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.*
import io.ygdrasil.webgpu.*
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.*
import kotlin.time.Duration

private var samplerIndex = 0

internal class ComposeWebgpuSurface(val ctx: WebGPUContextOld, val composeWindowLifecycle: ValueHolder<ComposeGlfwWindow>) : AutoCloseable {
    // For world input events, we need to ray trace to gui boxes, take the (x,y) on that surface, and pipe that (x,y) to the surface.
    private val inputListener = ctx.window.inputEvent.listenUnscoped { input ->
        val window = composeWindowLifecycle.value ?: return@listenUnscoped
        window.sendInputEvent(input)
    }
    private val densityListener = ctx.window.densityChangeEvent.listenUnscoped { newDensity ->
        val window = composeWindowLifecycle.value ?: return@listenUnscoped
        if (window.focused) {
            window.scene.density = newDensity
        }
    }

    val myIndex = samplerIndex++
    val sampler = ctx.device.createSampler(
        SamplerDescriptor(
            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear,
            label = "Compose Sampler #$myIndex"
        )
    )

    override fun close() {
        closeAll(sampler, inputListener, densityListener)
    }

    override fun toString(): String {
        return "Compose WebGPU Surface #$myIndex"
    }
}

private var textureIndex = 0

internal class ComposeTexture(val dimensions: WebGPUFixedSizeSurface, bgWindow: ComposeGlfwWindow, val ctx: WebGPUContext) : AutoCloseable {
    val myIndex = textureIndex++
    val composeTexture = dimensions.surface.device.createTexture(
        TextureDescriptor(
            size = Extent3D(dimensions.dimensions.width.toUInt(), dimensions.dimensions.height.toUInt(), 1u),
            format = GPUTextureFormat.RGBA8UnormSrgb,
            usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst),
            label = toString()
        )
    )


    init {
        // Need a new compose frame when the texture is recreated
        bgWindow.invalid = true
    }


    val listener = bgWindow.frameStream.listenUnscoped { (bytes, width, height) ->
        dimensions.surface.device.copyExternalImageToTexture(
            source = bytes,
            texture = composeTexture,
            width = width, height = height
        )
    }


    override fun toString(): String {
        return "Compose Texture #$myIndex w=${dimensions.dimensions.width},h=${dimensions.dimensions.height}"
    }


    override fun close() {
        closeAll(listener, composeTexture)
    }
}

internal class ComposeHudWebGPURenderer(
    private val hostWindow: WebGPUWindow,
    fsWatcher: FileSystemWatcher,
    beforeFrameEvent: EventStream<Duration>,
    onError: (Throwable) -> Unit,
    name: String,
    show: Boolean = false,
) {
    val compose = ComposeOpenGLRenderer(
        hostWindow.window.windowParameters,
        windowDimensionsLifecycle = hostWindow.window.dimensionsLifecycle,
        beforeFrameEvent = beforeFrameEvent,
        show = show, name = name, onError = onError, onSetPointerIcon = {
            setHostWindowCursorIcon(it)
        })
    val SurfaceLifecycleName = "$name Compose WebGPU Surface"

    private fun setHostWindowCursorIcon(icon: PointerIcon) {
        // Pick (or build) the native cursor to install
        val cursor: Long = when (icon) {
            PointerIcon.Default -> 0L                             // Arrow
            PointerIcon.Text -> glfwCursor(GLFW_IBEAM_CURSOR)           // I-beam
            PointerIcon.Crosshair -> glfwCursor(GLFW_CROSSHAIR_CURSOR)     // Crosshair
            PointerIcon.Hand -> glfwCursor(GLFW_HAND_CURSOR)            // Hand
            /* ---------- Anything else: just arrow ---------- */
            else -> 0L
        }

        GLFW.glfwSetCursor(hostWindow.window.windowLifecycle.assertValue.handle, cursor)
    }

    /** Cache of `glfwCreateStandardCursor` results so we don’t create the same cursor twice. */
    private val stdCursorCache = mutableMapOf<Int, Long>()

    /** Lazily create -– and cache –- one of GLFW’s built-in cursors. */
    private fun glfwCursor(shape: Int): Long = stdCursorCache.getOrPut(shape) { glfwCreateStandardCursor(shape) }


    val surfaceLifecycle = hostWindow.surfaceLifecycle.bind(SurfaceLifecycleName) { surface ->
        ComposeWebgpuSurface(surface, compose.windowLifecycle)
    }


    val fullscreenQuadLifecycle = createReloadingPipeline(
        "$name Compose Fullscreen Quad",
        hostWindow.surfaceLifecycle, fsWatcher,
        vertexShader = ShaderSource.HotFile("compose/fullscreen_quad.vertex"),
        fragmentShader = ShaderSource.HotFile("compose/fullscreen_quad.fragment"),
    ) { vertex, fragment ->
        // Allow transparency
        val colorState = ColorTargetState(
            format = presentationFormat,
            // Straight‑alpha blending:  out = src.rgb·src.a  +  dst.rgb·(1‑src.a)
            blend = BlendState(
                color = BlendComponent(
                    srcFactor = GPUBlendFactor.SrcAlpha,
                    dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                    operation = GPUBlendOperation.Add
                ),
                alpha = BlendComponent(
                    srcFactor = GPUBlendFactor.One,
                    dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                    operation = GPUBlendOperation.Add
                )
            ),
            writeMask = setOf(GPUColorWrite.All)
        )

        RenderPipelineDescriptor(
            layout = null,
            vertex = VertexState(
                module = vertex,
                entryPoint = "vs_main"
            ),
            fragment = FragmentState(
                module = fragment,
                targets = listOf(colorState),
                entryPoint = "fs_main",
            ),
            primitive = PrimitiveState(
                topology = GPUPrimitiveTopology.TriangleList
            ),
            label = "$name Compose Pipeline",
        )
    }

    val textureLifecycle = hostWindow.dimensionsLifecycle.bind(compose.windowLifecycle, "$name Compose Texture") { dim, bgWindow ->
        ComposeTexture(dim, bgWindow, dim.surface)
    }

    class ComposeBindGroup(pipeline: ReloadingPipeline, texture: ComposeTexture, surface: ComposeWebgpuSurface) : AutoCloseable {
        val resource = texture.composeTexture.createView()
        val group = texture.ctx.device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = surface.sampler
                    ),

                    BindGroupEntry(
                        binding = 1u,
                        resource = resource
                    )

                )
            )


        )

        override fun close() {
            closeAll(resource, group)
        }
    }


    val bindGroupLifecycle = fullscreenQuadLifecycle.bind(textureLifecycle, surfaceLifecycle, "$name Compose BindGroup") { pipeline, tex, surface ->
        ComposeBindGroup(pipeline, tex, surface)
    }

    val frameLifecycle = fullscreenQuadLifecycle.bind(bindGroupLifecycle, "$name Compose Frame") { pipeline, group ->
        ComposeFrame(pipeline, group)
    }


    /**
     * Should be called every frame to draw Compose content
     */
    fun frame(encoder: GPUCommandEncoder, drawTarget: GPUTextureView, composeFrame: ComposeFrame) {
        val renderPassDescriptor = RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = drawTarget,
                    clearValue = Color(0.0, 0.0, 0.0, 0.0),
                    loadOp = GPULoadOp.Load, // Keep the previous content, we want to overlay on top of it
                    storeOp = GPUStoreOp.Store
                ),
            ),
        )

        // Create bind group for the sampler, and texture
        val pass = encoder.beginRenderPass(renderPassDescriptor)
        pass.setPipeline(composeFrame.pipeline.pipeline)
        pass.setBindGroup(0u, composeFrame.bindGroup.group)
        pass.draw(6u)
        pass.end()
    }
}

internal data class ComposeFrame(
    val pipeline: ReloadingPipeline, val bindGroup: ComposeBindGroup,
)