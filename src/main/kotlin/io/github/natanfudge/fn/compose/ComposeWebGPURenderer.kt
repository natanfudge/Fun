package io.github.natanfudge.fn.compose

import androidx.compose.runtime.Composable
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer.ComposeBindGroup
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.WindowDimensions
import io.ygdrasil.webgpu.*
import java.util.function.Consumer

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



//    init {
//        // Need a new compose frame when the texture is recreated
//        compose.dimensionsLifecycle.value?.invalid = true
//    }

    init {
        println("Registering listener with dim= ${dimensions.dimensions}")
    }


    //
    private val context = ComposeTextureContext(dimensions.surface.device,composeTexture, dimensions.dimensions)

    //TODO we can def pass a lambda, the bug is something else
    val listener  = bgWindow.frameStream.listen(context)

//    val listener = compose.frameStream.listen { (bytes, width, height) ->
//        println("Writing compose texture for dimensions ${dimensions.dimensions}")
//        //TODO: restore this when we figure out how to fix it with reload -> resize
//        dimensions.surface.device.copyExternalImageToTexture(
//            source = bytes,
//            texture = composeTexture,
//            width = width, height = height
//        )
//    }







    override fun toString(): String {
        return "Compose Texture #$myIndex w=${dimensions.dimensions.width},h=${dimensions.dimensions.height}"
    }



    override fun close() {
        println("Closing listener with dim= ${dimensions.dimensions}. Index = ${context.index}. I am $myIndex")
        closeAll(listener, composeTexture)
    }
}






private var texCtxIndex = 0

private class ComposeTextureContext(val device: GPUDevice, val texture: GPUTexture,val textureDim: WindowDimensions): Consumer<ComposeFrameEvent> {
    val index = texCtxIndex++

    init {
        println("Creating ComposeTextureContext #$index")
    }

    override fun accept(t: ComposeFrameEvent) {
        println("Writing compose texture for dimensions ${t.width}x${t.height}, with textureDim = $textureDim. I am #$index")
        device.copyExternalImageToTexture(
            source = t.bytes,
            texture = texture,
            width = t.width, height = t.height
        )
    }

    override fun toString(): String {
        return "ComposeTextureContext #$index"
    }
}


class ComposeWebGPURenderer(
    hostWindow: WebGPUWindow,
    fsWatcher: FileSystemWatcher,
    show: Boolean = false,
    content: @Composable () -> Unit,
) {
    private val compose = ComposeConfig(hostWindow.window, content, show = show)


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


//    val fullscreenQuad by fullscreenQuadLifecycle

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
            val x = 2
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
//        val pipeline = fullscreenQuadLifecycle.assertValue.pipeline
//        println("Compose pipeline = $pipeline")
        pass.setPipeline(composeFrame.pipeline.pipeline)
//        pass.setPipeline()
        pass.setBindGroup(0u, composeFrame.bindGroup.group)
        pass.draw(6u)
        pass.end()
    }


//    fun frame(encoder: GPUCommandEncoder, drawTarget: GPUTextureView) {
//
//
//    }




    /**
     * These callbacks should be called when these events occur to let Compose know what is happening
     */
    val callbacks = compose.callbacks

//    fun restart() = compose.restart()
}

data class ComposeFrame(
    val pipeline: ReloadingPipeline, val bindGroup: ComposeBindGroup
)