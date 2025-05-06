package io.github.natanfudge.fn.compose

import androidx.compose.runtime.Composable
import io.github.natanfudge.fn.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.StatefulLifecycle
import io.github.natanfudge.fn.util.bindState
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.WindowConfig
import io.github.natanfudge.fn.window.WindowDimensions
import io.ygdrasil.webgpu.*

class ComposeWebgpuSurface(val ctx: WebGPUContext) : AutoCloseable {
    val fsWatcher = FileSystemWatcher()

    // Allow transparency
    val colorState = ColorTargetState(
        format = ctx.presentationFormat,
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

    val fullscreenQuad = ReloadingPipeline(
        ctx.device, fsWatcher,
        vertexShader = ShaderSource.HotFile("compose/fullscreen_quad.vertex"),
        fragmentShader = ShaderSource.HotFile("compose/fullscreen_quad.fragment"),
        hotReloadShaders = HOT_RELOAD_SHADERS
    ) { vertex, fragment ->
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
            label = "Compose Pipeline",
        )
    }

    val sampler = ctx.device.createSampler(
        SamplerDescriptor(
            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear
        )
    )

    override fun close() {
        closeAll(fsWatcher, fullscreenQuad, sampler)
    }
}

//TODO: maybe make this inner class
class ComposeTexture(dimensions: WindowDimensions, window: StatefulLifecycle<*, ComposeWebgpuSurface>, compose: GlfwComposeWindow) : AutoCloseable {
    val composeTexture = window.assertValue.ctx.device.createTexture(
        TextureDescriptor(
            size = Extent3D(dimensions.width.toUInt(), dimensions.height.toUInt(), 1u),
            format = GPUTextureFormat.RGBA8UnormSrgb,
            usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
        )
    )

    val listener = compose.frame.listen { (bytes, width, height) ->
//            val mismatchingDim = width != textureWidth || height != textureHeight
//            if (mismatchingDim) {
//                textureWidth = width
//                textureHeight = height
//                composeTexture = device.createTexture(
//                    TextureDescriptor(
//                        size = Extent3D(textureWidth.toUInt(), textureHeight.toUInt(), 1u),
//                        format = GPUTextureFormat.RGBA8UnormSrgb,
//                        usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
//                    )
//                ).ac
//            }
        window.assertValue.ctx.device.copyExternalImageToTexture(
            source = bytes,
            texture = composeTexture,
            width = width, height = height
        )
    }

    override fun close() {
        closeAll(listener, composeTexture)
    }
}

class ComposeWebGPURenderer(private val config: WindowConfig, hostWindow: WebGPUWindow, show: Boolean = false, content: @Composable () -> Unit) {
    private val compose = GlfwComposeWindow(content, show = show)

    init {
        compose.show(config)
    }

//    var textureWidth = config.initialWindowWidth
//    var textureHeight = config.initialWindowHeight
//    lateinit var composeTexture: GPUTexture
//    lateinit var sampler: GPUSampler
//    lateinit var fullscreenQuad: ReloadingPipeline

    val surfaceLifecycle = hostWindow.surfaceLifecycle.bindState("Compose WebGPU Surface") {
        ComposeWebgpuSurface(it)
    }

    val surface by surfaceLifecycle

    val texture by hostWindow.dimensionsLifecycle.bindState("Compose Texture") {
        ComposeTexture(it, surfaceLifecycle,compose)
    }

//    /**
//     * Should be called during webgpu initialization
//     */
//    fun init(device: GPUDevice, ac: AutoClose, fsWatcher: FileSystemWatcher, presentationFormat: GPUTextureFormat) = with(ac) {
//
//
//        //TODO: with better "lifecycle integration" this would look better...
//        composeTexture = device.createTexture(
//            TextureDescriptor(
//                size = Extent3D(textureWidth.toUInt(), textureHeight.toUInt(), 1u),
//                format = GPUTextureFormat.RGBA8UnormSrgb,
//                usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
//            )
//        ).ac
//
//        sampler = device.createSampler(
//            SamplerDescriptor(
//                magFilter = GPUFilterMode.Linear,
//                minFilter = GPUFilterMode.Linear
//            )
//        ).ac
//
//        compose.frame.listen { (bytes, width, height) ->
////            val mismatchingDim = width != textureWidth || height != textureHeight
////            if (mismatchingDim) {
////                textureWidth = width
////                textureHeight = height
////                composeTexture = device.createTexture(
////                    TextureDescriptor(
////                        size = Extent3D(textureWidth.toUInt(), textureHeight.toUInt(), 1u),
////                        format = GPUTextureFormat.RGBA8UnormSrgb,
////                        usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
////                    )
////                ).ac
////            }
//            device.copyExternalImageToTexture(
//                source = bytes,
//                texture = composeTexture,
//                width = width, height = height
//            )
//        }
//    }

    /**
     * Should be called every frame to draw Compose content
     */
    fun frame(device: GPUDevice, ac: AutoClose, encoder: GPUCommandEncoder, drawTarget: GPUTextureView) = with(ac) {
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
        val bindGroup = device.createBindGroup(
            BindGroupDescriptor(
                layout = surface.fullscreenQuad.pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = surface.sampler
                    ),
                    BindGroupEntry(
                        binding = 1u,
                        resource = texture.composeTexture.createView()
                    )
                )
            )
        ).ac
        val pass = encoder.beginRenderPass(renderPassDescriptor)
        pass.setPipeline(surface.fullscreenQuad.pipeline)
        pass.setBindGroup(0u, bindGroup)
        pass.draw(6u)
        pass.end()
    }

    /**
     * These callbacks should be called when these events occur to let Compose know what is happening
     */
    val callbacks = compose.callbacks

    fun restart(config: WindowConfig = WindowConfig()) = compose.restart(config)
}