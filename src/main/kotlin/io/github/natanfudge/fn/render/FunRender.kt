package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.core.FunApp
import io.github.natanfudge.fn.core.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.FunLogLevel
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.webgpu.createReloadingPipeline
import io.github.natanfudge.fn.window.WindowCallbacks
import io.github.natanfudge.fn.window.WindowDimensions
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.roundToInt

// 1. Program a WGSL visual debugger

val msaaSamples = 4u


class FunFixedSizeWindow(ctx: WebGPUContext, val dims: WindowDimensions) : AutoCloseable {
    val extent = Extent3D(dims.width.toUInt(), dims.height.toUInt())

    // Create z buffer
    val depthTexture = ctx.device.createTexture(
        TextureDescriptor(
            size = extent,
            format = GPUTextureFormat.Depth24Plus,
            usage = setOf(GPUTextureUsage.RenderAttachment),
            sampleCount = msaaSamples
        )
    )
    val depthStencilView = depthTexture.createView()

    val msaaTexture = ctx.device.createTexture(
        TextureDescriptor(
            size = extent,
            sampleCount = msaaSamples,
            format = ctx.presentationFormat,
            usage = setOf(GPUTextureUsage.RenderAttachment)
        )
    )

    val msaaTextureView = msaaTexture.createView()

    val projection = Mat4f.perspective(
        fieldOfViewYInRadians = PI.toFloat() / 3,
        aspect = dims.width.toFloat() / dims.height,
        zNear = 0.01f,
        zFar = 100f
    )


//    val fovY = Math.PI.toFloat() / 3f      // ~60°
//    val aspect = dims.width.toFloat() / dims.height
//    val zMatch = 1.5f                       // depth where sizes should match
//
//    val halfHeight = (tan(fovY / 2f) * zMatch)
//    val halfWidth  = halfHeight * aspect
//
//    val projection = Mat4f.orthographic(
//        left   = -halfWidth,
//        right  =  halfWidth,
//        bottom = -halfHeight,
//        top    =  halfHeight,
//        near   = 0.01f,   // same near/far you used before is fine
//        far    = 100f
//    )


    override fun close() {
        closeAll(depthStencilView, depthTexture, msaaTextureView, msaaTexture)
    }
}


class FunSurface(val ctx: WebGPUContext,) : AutoCloseable {
    val sampler = ctx.device.createSampler()

    val world = WorldRender(ctx,this)

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

//class AppState(window: WebGPUWindow, compose: ComposeWebGPURenderer) {
//    val camera = DefaultCamera()
//    val input = InputManager(this, window, compose)
//}

class FunInputAdapter(private val app: FunApp) : WindowCallbacks {
    override fun onInput(input: InputEvent) {
        app.handleInput(input)
    }
}

data class AppSurfaceBinding(
    val app: FunApp,
    val surface: FunSurface
)

@OptIn(ExperimentalAtomicApi::class)
fun WebGPUWindow.bindFunLifecycles(
    compose: ComposeWebGPURenderer,
    fsWatcher: FileSystemWatcher,
    appLifecycle: Lifecycle<*, FunApp>,
    funSurface: Lifecycle<*, FunSurface>,
    funDimLifecycle: Lifecycle<*, FunFixedSizeWindow>,
) {
//    val appLifecycle = ProcessLifecycle.bind("App") {
//        AppState(this@bindFunLifecycles, compose)
//    }

    val appSurface = funSurface.bind(appLifecycle, "App<->Surface Binding") { surface, app ->
        surface.ctx.window.callbacks["Fun"] = FunInputAdapter(app)
//        app.renderInit(surface.world)
        AppSurfaceBinding(app,surface)
    }



    val objectLifecycle = createReloadingPipeline(
        "Object",
        surfaceLifecycle, fsWatcher,
        vertexShader = ShaderSource.HotFile("object"),
    ) { vertex, fragment ->
        pipelines++
        println("Creating pipeline $pipelines")
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
                            VertexAttribute(format = GPUVertexFormat.Float32x3, offset = Vec3f.ACTUAL_SIZE_BYTES.toULong(), shaderLocation = 1u), // Normal
                            VertexAttribute(format = GPUVertexFormat.Float32x2, offset = Vec3f.ACTUAL_SIZE_BYTES.toULong() * 2u, shaderLocation = 2u), // uv
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

    var prevPhysicsTime = System.nanoTime()

    // Running this in-frame is a bad idea since it can trigger a new frame
    window.eventPollLifecycle.bind(appLifecycle, "Fun Polling", FunLogLevel.Verbose) { _, app ->
        if (HOT_RELOAD_SHADERS) {
            fsWatcher.poll()
        }
        val physicsDelta = System.nanoTime() - prevPhysicsTime
        prevPhysicsTime = System.nanoTime()
        app.physics(physicsDelta / 1e6f)
//        app.preFrame()
    }


    frameLifecycle.bind(
        funSurface, funDimLifecycle, bindGroupLifecycle, objectLifecycle,
        compose.frameLifecycle, appLifecycle,
        "Fun Frame", FunLogLevel.Verbose
    ) { frame, surface, dimensions, bindGroup, shaders, composeFrame, app ->
        val ctx = frame.ctx
        checkForFrameDrops(ctx, frame.deltaMs)
        val commandEncoder = ctx.device.createCommandEncoder()

        val textureView = frame.windowTexture

        //TODo: need to think how to enable user-driven drawing

        surface.world.draw(commandEncoder, bindGroup, shaders.pipeline, dimensions, textureView, cursorPosition = surface.ctx.window.cursorPos,camera = app.camera)

        compose.frame(commandEncoder, textureView, composeFrame)


        val err = ctx.error
        if (err != null) {
            ctx.error = null
            throw err
        }

        ctx.device.queue.submit(listOf(commandEncoder.finish()));


        commandEncoder
    }

}


interface Camera {
    val viewMatrix: Mat4f
    val position: Vec3f
    val forward: Vec3f
}


private fun checkForFrameDrops(window: WebGPUContext, deltaMs: Double) {
    val normalFrameTimeMs = (1f / window.refreshRate) * 1000
    // It's not exact, but if it's almost twice as long (or more), it means we took too much time to make a frame
    if (deltaMs > normalFrameTimeMs * 1.8f) {
        val missingFrames = (deltaMs / normalFrameTimeMs).roundToInt() - 1
        val plural = missingFrames > 1
        println(
            "Took ${deltaMs}ms to make a frame instead of the usual ${normalFrameTimeMs.roundToInt()}ms," +
                    " so about $missingFrames ${if (plural) "frames were" else "frame was"} dropped"
        )
    }
}
