package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.compose.ComposeHudWebGPURenderer
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunContextRegistry
import io.github.natanfudge.fn.core.FunLogLevel
import io.github.natanfudge.fn.core.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.WebGPUContextOld
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.webgpu.createReloadingPipeline
import io.github.natanfudge.fn.window.GlfwWindowDimensions
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.*
import korlibs.time.milliseconds
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.roundToInt

// 1. Program a WGSL visual debugger

val msaaSamples = 4u


class FunWindow(ctx: WebGPUContext, val dims: GlfwWindowDimensions) : AutoCloseable {
    val extent = Extent3D(dims.width.toUInt(), dims.height.toUInt())
    val height: Int = dims.height
    val width: Int = dims.width

    // Create z buffer
    val depthTexture = ctx.device.createTexture(
        TextureDescriptor(
            size = extent,
            format = GPUTextureFormat.Depth24Plus,
            usage = setOf(GPUTextureUsage.RenderAttachment),
            sampleCount = msaaSamples,
            label = "Depth Texture"
        )
    )
    val depthStencilView = depthTexture.createView()

    val msaaTexture = ctx.device.createTexture(
        TextureDescriptor(
            size = extent,
            sampleCount = msaaSamples,
            format = ctx.presentationFormat,
            usage = setOf(GPUTextureUsage.RenderAttachment),
            label = "MSAA Texture"
        )
    )

    val msaaTextureView = msaaTexture.createView()


    private fun calculateProjectionMatrix() = Mat4f.perspective(
        fieldOfViewYInRadians = fovYRadians,
        aspect = aspectRatio,
        zNear = 0.01f,
        zFar = 100f
    )

    var fovYRadians = PI.toFloat() / 3f
        set(value) {
            field = value
            calculateProjectionMatrix()
        }
    val aspectRatio = dims.width.toFloat() / dims.height


    val projection = calculateProjectionMatrix()


    override fun close() {
        closeAll(depthStencilView, depthTexture, msaaTextureView, msaaTexture)
    }
}


class FunSurface(val ctx: WebGPUContextOld) : AutoCloseable {
    val sampler = ctx.device.createSampler(
        SamplerDescriptor(
            label = "Fun Sampler",

            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear,
            mipmapFilter = GPUMipmapFilterMode.Linear,
            maxAnisotropy = 8u
        ),
    )

    val world = WorldRender(ctx, this)

    override fun close() {
        closeAll(sampler)
    }
}


private val alignmentBytes = 16u
internal fun ULong.wgpuAlign(): ULong {
    val leftOver = this % alignmentBytes
    return if (leftOver == 0uL) this else ((this / alignmentBytes) + 1u) * alignmentBytes
}

internal fun UInt.wgpuAlignInt(): UInt = toULong().wgpuAlign().toUInt()

var pipelines = 0





@OptIn(ExperimentalAtomicApi::class)
internal fun WebGPUWindow.bindFunLifecycles(
    compose: ComposeHudWebGPURenderer,
    fsWatcher: FileSystemWatcher,
    appLifecycle: Lifecycle<FunContext>,
    funSurface: Lifecycle<FunSurface>,
    funDimLifecycle: Lifecycle<FunWindow>,
) {

    //SUS: this should be part of WorldRender
    val objectLifecycle = createReloadingPipeline(
        "Object",
        surfaceLifecycle, fsWatcher,
        vertexShader = ShaderSource.HotFile("object"),
    ) { vertex, fragment ->
        pipelines++
        RenderPipelineDescriptor(
            label = "Fun Object Pipeline #$pipelines",
            vertex = VertexState(
                module = vertex,
                entryPoint = "vs_main",
                buffers = listOf(
                    VertexBufferLayout(
                        arrayStride = VertexArrayBuffer.StrideBytes,
                        attributes = listOf(
                            VertexAttribute(format = GPUVertexFormat.Float32x3, offset = 0uL, shaderLocation = 0u), // Position
                            VertexAttribute(format = GPUVertexFormat.Float32x3, offset = 12uL, shaderLocation = 1u), // Normal
                            VertexAttribute(format = GPUVertexFormat.Float32x2, offset = 24uL, shaderLocation = 2u), // uv
                            VertexAttribute(format = GPUVertexFormat.Float32x4, offset = 32uL, shaderLocation = 3u), // joints
                            VertexAttribute(format = GPUVertexFormat.Float32x4, offset = 48uL, shaderLocation = 4u), // weights
                        )
                    ),
                )
            ),
            fragment = FragmentState(
                module = fragment,
                entryPoint = "fs_main",
                targets = listOf(
                    ColorTargetState(
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
                )
            ),

            primitive = PrimitiveState(
                topology = GPUPrimitiveTopology.TriangleList,
//                cullMode = GPUCullMode.Back
                cullMode = GPUCullMode.None
            ),
            depthStencil = DepthStencilState(
                format = GPUTextureFormat.Depth24Plus,
                depthWriteEnabled = true,
                depthCompare = GPUCompareFunction.Less
            ),
            multisample = MultisampleState(count = msaaSamples)
        )
    }


    // We need bindgroups to be created after
    val bindGroupLifecycle = objectLifecycle.bind(funSurface, "Fun BindGroup") { pipeline, surface ->
        surface.world.onPipelineChanged(pipeline.pipeline)
        surface.world.createBindGroup(pipeline.pipeline)
    }


    // Running this in-frame is a bad idea since it can trigger a new frame
    window.eventPollLifecycle.bind(appLifecycle, "Fun Polling", FunLogLevel.Verbose) { _, context ->
        if (HOT_RELOAD_SHADERS) {
            fsWatcher.poll()
        }
        context.time._poll()
    }


    frameLifecycle.bind(
        funSurface, funDimLifecycle, bindGroupLifecycle, objectLifecycle,
        compose.frameLifecycle, appLifecycle,
        "Fun Frame", FunLogLevel.Verbose
    ) { frame, surface, dimensions, bindGroup, shaders, composeFrame, context ->
        if (!frame.isReady) return@bind
        frame.isReady = false // Avoid drawing using the same parent frame twice

        context.events.beforeFrame.emit(frame.deltaMs.milliseconds)

        val ctx = frame.ctx
        checkForFrameDrops(ctx, frame.deltaMs)
        val commandEncoder = ctx.device.createCommandEncoder()

        val textureView = frame.windowTexture

        //TO Do: need to think how to enable user-driven drawing

        surface.world.draw(commandEncoder, bindGroup, shaders.pipeline, dimensions, textureView, camera = context.camera)

        compose.frame(commandEncoder, textureView, composeFrame)


        val err = ctx.error
        if (err != null) {
            ctx.error = null
            throw err
        }

        ctx.device.queue.submit(listOf(commandEncoder.finish()));

        ctx.surface.present()

        commandEncoder
    }

}


interface Camera {
    val viewMatrix: Mat4f
    val position: Vec3f
    val forward: Vec3f
}


private fun checkForFrameDrops(window: WebGPUContextOld, deltaMs: Double) {
    val normalFrameTimeMs = (1f / window.refreshRate) * 1000
    // It's not exact, but if it's almost twice as long (or more), it means we took too much time to make a frame
    if (deltaMs > normalFrameTimeMs * 1.8f) {
        val missingFrames = (deltaMs / normalFrameTimeMs).roundToInt() - 1
        val plural = missingFrames > 1
        FunContextRegistry.getContext().logger.performance("Frame Drops") {
            "Took ${deltaMs}ms to make a frame instead of the usual ${normalFrameTimeMs.roundToInt()}ms," +
                    " so about $missingFrames ${if (plural) "frames were" else "frame was"} dropped"
        }
    }
}
