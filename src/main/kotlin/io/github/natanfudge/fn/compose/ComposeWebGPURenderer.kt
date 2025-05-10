package io.github.natanfudge.fn.compose

import androidx.compose.runtime.Composable
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.*
import io.ygdrasil.webgpu.*

class ComposeWebgpuSurface(val ctx: WebGPUContext) : AutoCloseable {

    val sampler = ctx.device.createSampler(
        SamplerDescriptor(
            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear
        )
    )

    override fun close() {
        closeAll(sampler)
    }
}

class ComposeTexture(dimensions: WebGPUFixedSizeSurface, compose: GlfwComposeWindow, val ctx: WebGPUContext) : AutoCloseable {
    val composeTexture = dimensions.surface.device.createTexture(
        TextureDescriptor(
            size = Extent3D(dimensions.dimensions.width.toUInt(), dimensions.dimensions.height.toUInt(), 1u),
            format = GPUTextureFormat.RGBA8UnormSrgb,
            usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
        )
    )

    init {
        // Need a new compose frame when the texture is recreated
        compose.dimensionsLifecycle.value?.invalid = true
    }

    val listener = compose.frame.listen { (bytes, width, height) ->
        println("Writing compose texture")
        dimensions.surface.device.copyExternalImageToTexture(
            source = bytes,
            texture = composeTexture,
            width = width, height = height
        )
    }

    override fun close() {
        closeAll(listener, composeTexture)
    }
}

class ComposeWebGPURenderer(
    hostWindow: WebGPUWindow,
    fsWatcher: FileSystemWatcher,
    show: Boolean = false,
    content: @Composable () -> Unit,
) {
    private val compose = GlfwComposeWindow(hostWindow.window, content, show = show)


//    val BackgroundWindowLifecycle = ProcessLifecycle.bind("Compose Background Window", early = true) {
//        compose.show(config)
//    }


//    fun show() {
//        compose.show(config)
//    }

//    init {
//        compose.show(config)
//    }

    val surfaceLifecycle = hostWindow.surfaceLifecycle.bind("Compose WebGPU Surface") {
        ComposeWebgpuSurface(it)
    }


    val fullscreenQuadLifecycle = createReloadingPipeline(
        "Compose Fullscreen Quad",
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
            label = "Compose Pipeline",
        )
    }

    val fullscreenQuad by fullscreenQuadLifecycle

    val surface by surfaceLifecycle

    val textureLifecycle = hostWindow.dimensionsLifecycle.bind("Compose Texture") {
        ComposeTexture(it, compose, it.surface)
    }


    val bindGroup by fullscreenQuadLifecycle.bind2(textureLifecycle, "Compose BindGroup") { pipeline, tex ->
        val group = tex.ctx.device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = surface.sampler // HACK: we likely need a third dependency on the Compose Surface lifecycle
                    ),
                    BindGroupEntry(
                        binding = 1u,
                        resource = tex.composeTexture.createView()
                    )
                )
            )
        )
        group
    }

    /**
     * Should be called every frame to draw Compose content
     */
    fun frame(encoder: GPUCommandEncoder, drawTarget: GPUTextureView) {

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
        pass.setPipeline(fullscreenQuad.pipeline)
        pass.setBindGroup(0u, bindGroup)
        pass.draw(6u)
        pass.end()
    }

    /**
     * These callbacks should be called when these events occur to let Compose know what is happening
     */
    val callbacks = compose.callbacks

    fun restart() = compose.restart()
}