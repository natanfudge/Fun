package io.github.natanfudge.fn.compose

import io.github.natanfudge.fn.compose.ComposeWebGPURenderer.ComposeBindGroup
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.*
import io.ygdrasil.webgpu.*

private var samplerIndex = 0
class ComposeWebgpuSurface(val ctx: WebGPUContext) : AutoCloseable {

    val myIndex = samplerIndex++
    val sampler = ctx.device.createSampler(
        SamplerDescriptor(
            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear,
            label = "Compose Sampler #$myIndex"
        )
    )

    override fun close() {
        closeAll(sampler)
    }

    override fun toString(): String {
        return "Compose WebGPU Surface #$myIndex"
    }
}

private var textureIndex = 0

class ComposeTexture(val dimensions: WebGPUFixedSizeSurface, bgWindow: ComposeGlfwWindow, val ctx: WebGPUContext) : AutoCloseable {
    val myIndex=  textureIndex++
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

class ComposeWebGPURenderer(
    hostWindow: WebGPUWindow,
    fsWatcher: FileSystemWatcher,
    onError: (Throwable) -> Unit,
    show: Boolean = false
) {
     val compose = ComposeConfig(hostWindow.window,  show = show, onError)

    companion object {
        const val SurfaceLifecycleName = "Compose WebGPU Surface"
    }

    val surfaceLifecycle = hostWindow.surfaceLifecycle.bind(SurfaceLifecycleName) {
        it.window.callbacks["Compose"] = compose.callbacks
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

    val textureLifecycle = hostWindow.dimensionsLifecycle.bind(compose.windowLifecycle, "Compose Texture") {dim, bgWindow ->
        ComposeTexture(dim, bgWindow, dim.surface)
    }

    class ComposeBindGroup(pipeline: ReloadingPipeline,texture: ComposeTexture, surface: ComposeWebgpuSurface): AutoCloseable {
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



    val bindGroupLifecycle = fullscreenQuadLifecycle.bind(textureLifecycle, surfaceLifecycle,"Compose BindGroup") { pipeline, tex, surface ->
        ComposeBindGroup(pipeline, tex, surface)
    }

    val frameLifecycle = fullscreenQuadLifecycle.bind(bindGroupLifecycle,"Compose Frame"){ pipeline, group ->
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

data class ComposeFrame(
    val pipeline: ReloadingPipeline, val bindGroup: ComposeBindGroup
)