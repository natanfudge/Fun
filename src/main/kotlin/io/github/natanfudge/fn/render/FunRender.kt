package io.github.natanfudge.fn.render

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.core.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.files.readImage
import io.github.natanfudge.fn.util.FunLogLevel
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.webgpu.createReloadingPipeline
import io.github.natanfudge.fn.window.WindowDimensions
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.github.natanfudge.wgpu4k.matrix.Vec4f
import io.ygdrasil.webgpu.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.tan

// 1. Program a WGSL visual debugger
//TODO:
// 12b. Abstraction of entity system
// 13. Ray casting & selection
// 13b. Targeted zoom (with ray casting?)
// 14. Physics
// 15. Trying making a basic game
// 16. Integrate the Fun "ECS" system and allow assigning physicality to components, allowing you to click on things and view their state
// 17. PBR

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
//    val zMatch = 2f                       // depth where sizes should match
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

class FunSurface(val ctx: WebGPUContext) : AutoCloseable {
//    val cubeMesh = Mesh.UnitCube()
//    val cube = Model(cubeMesh).bind(ctx)

//    val sphereMesh: Mesh = Mesh.sphere()
//    val sphere = Model(sphereMesh).bind(ctx)

//    val cubeVerticesBuffer = ctx.device.createBuffer(
//        BufferDescriptor(
//            size = cubeMesh.verticesByteSize, // x,y,z for each vertex, total 3 coords, each coord is a float so 4 bytes
//            usage = setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
//            mappedAtCreation = true
//        )
//    ).also {
//        cubeMesh.vertices.array.writeInto(it.getMappedRange())
//        it.unmap()
//    }
//
//
//    val cubeIndicesBuffer = ctx.device.createBuffer(
//        BufferDescriptor(
//            size = cubeMesh.indexCount * Float.SIZE_BYTES.toULong(),
//            usage = setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
//            mappedAtCreation = true
//        )
//    ).also {
//        it.mapFrom(cubeMesh.indices.array)
//        it.unmap()
//    }


//    val sphereIndicesBuffer = ctx.device.createBuffer(
//        BufferDescriptor(
//            size = sphereMesh.indexCount * Float.SIZE_BYTES.toULong(),
//            usage = setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
////            mappedAtCreation = true
//        )
//    ).also {
//        ctx.device.queue.writeBuffer(it, 0u, sphereMesh.indices.array)
////        it.mapFrom(sphereMesh.indices.array)
////        it.unmap()
//    }
//
//    val sphereVerticesBuffer = ctx.device.createBuffer(
//        BufferDescriptor(
//            size = sphereMesh.verticesByteSize, // x,y,z for each vertex, total 3 coords, each coord is a float so 4 bytes
//            usage = setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
////            mappedAtCreation = true
//        )
//    ).also {
//        ctx.device.queue.writeBuffer(it, 0u, sphereMesh.vertices.array)
////        sphereMesh.vertices.array.writeInto(it.getMappedRange())
////        it.unmap()
//    }


    val uniformBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = (Mat4f.SIZE_BYTES.toULong() + Vec3f.ALIGN_BYTES * 2u + Float.SIZE_BYTES.toULong() * 2uL).wgpuAlign(),
            usage = setOf(GPUBufferUsage.Uniform, GPUBufferUsage.CopyDst)
        )
    )

    val maxInstances = 10uL
    val instanceStride = (Mat4f.SIZE_BYTES + Mat3f.SIZE_BYTES + Vec4f.SIZE_BYTES).wgpuAlign()

    //    val storageBuffer = ctx.device.createBuffer(
//        BufferDescriptor(
//            size = instanceStride * maxInstances, // Include space for both model matrix and color
//            usage = setOf(GPUBufferUsage.Storage, GPUBufferUsage.CopyDst)
//        )
//    )
    val kotlinImage = readImage(kotlinx.io.files.Path("src/main/composeResources/drawable/Kotlin_Icon.png"))
    val wgpu4kImage = readImage(kotlinx.io.files.Path("src/main/composeResources/drawable/wgpu4k.png"))



//    val kotlinTexture = ctx.device.createTexture(
//        TextureDescriptor(
//            size = Extent3D(image.width.toUInt(), image.height.toUInt(), 1u),
//            // We loaded srgb data from the png so we specify srgb here. If your data is in a linear color space you can do RGBA8Unorm instead
//            format = GPUTextureFormat.RGBA8UnormSrgb,
//            usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
//        )
//    )
//
//    val kotlinTextureView = kotlinTexture.createView()


//    init {
//        ctx.device.copyExternalImageToTexture(
//            source = image.bytes,
//            texture = kotlinTexture,
//            width = image.width, height = image.height
//        )
//    }


    val sampler = ctx.device.createSampler()


    override fun close() {
        closeAll(
            uniformBuffer, sampler,
        )
    }
}

private val alignmentBytes = 16u
internal fun ULong.wgpuAlign(): ULong {
    val leftOver = this % alignmentBytes
    return if (leftOver == 0uL) this else ((this / alignmentBytes) + 1u) * alignmentBytes
}

internal fun UInt.wgpuAlign(): ULong = toULong().wgpuAlign()

var pipelines = 0

class AppState(window: WebGPUWindow, compose: ComposeWebGPURenderer) {
    val camera = Camera()
    val input = InputManager(this, window, compose)
}


@OptIn(ExperimentalAtomicApi::class)
fun WebGPUWindow.bindFunLifecycles(compose: ComposeWebGPURenderer, fsWatcher: FileSystemWatcher) {
    val appLifecycle = ProcessLifecycle.bind("App") {
        AppState(this@bindFunLifecycles, compose)
    }

    surfaceLifecycle.bind(appLifecycle, "Fun callback set") { surface, app ->
        surface.window.callbacks["Fun"] = app.input.callbacks
    }

    val funSurface = surfaceLifecycle.bind("Fun Surface") { surface ->
        FunSurface(surface)
    }


    val funDimLifecycle = dimensionsLifecycle.bind("Fun Dimensions") {
        FunFixedSizeWindow(it.surface, it.dimensions)
    }

//    compose.compose.windowLifecycle.bind("Fun Compose Lock") {
////        it.focused = false
//    }


    val cubeLifecycle = createReloadingPipeline(
        "Cube",
        surfaceLifecycle, fsWatcher,
        vertexShader = ShaderSource.HotFile("cube"),
    ) { vertex, fragment ->
        pipelines++
        println("Creating pipeline $pipelines")
        RenderPipelineDescriptor(
            label = "Fun Cube Pipeline #$pipelines",
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
                targets = listOf(ColorTargetState(
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
                ))
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

    class BindGroups(
        val main: GPUBindGroup,
        val material: GPUBindGroup, // Temporary until we have bindless resources. For now we will need to bind this individually for each model
    )

    val lightPos = Vec3f(4f, -4f, 4f)

    val bindGroupLifecycle = cubeLifecycle.bind(funSurface, "Fun BindGroup") { pipeline, surface ->
        WorldRender(surface.ctx, pipeline.pipeline, surface).also {
            val cubeModel = Model(Mesh.UnitCube())
            val sphereModel = Model(Mesh.uvSphere())
            val cube = it.bind(cubeModel)
            val sphere = it.bind(sphereModel)
//            cube.spawn(Mat4f.scaling(x = 10f, y = 0.1f, z = 0.1f), Color.Red) // X axis
//            cube.spawn(Mat4f.scaling(x = 0.1f, y = 10f, z = 0.1f), Color.Green) // Y Axis
//            cube.spawn(Mat4f.scaling(x = 0.1f, y = 0.1f, z = 10f), Color.Blue) // Z Axis
//            cube.spawn(Mat4f.translation(0f, 0f, -1f).scale(x = 10f, y = 10f, z = 0.1f), Color.Gray)

//            val kotlinSphere = it.bind(sphereModel.copy(material = Material(texture = surface.kotlinImage)))

            val wgpuCube = it.bind(Model(Mesh.UnitCube(CubeUv.Grid3x2), Material(surface.wgpu4kImage)))

            wgpuCube.spawn()

//            kotlinSphere.spawn()
//            sphere.spawn(Mat4f.translation(2f, 2f, 2f))
            sphere.spawn(Mat4f.translation(lightPos).scale(0.2f))

//            cube.spawn(Mat4f.translation(4f, -4f, 0.5f), Color.Gray)
        }
    }


    // Running this in-frame is a bad idea since it can trigger a new frame
    window.eventPollLifecycle.bind("Fun Polling", FunLogLevel.Verbose) {
        if (HOT_RELOAD_SHADERS) {
            fsWatcher.poll()
        }
        appLifecycle.assertValue.input.poll()
    }


    frameLifecycle.bind(
        funSurface, funDimLifecycle, bindGroupLifecycle, cubeLifecycle,
        compose.frameLifecycle, appLifecycle,
        "Fun Frame", FunLogLevel.Verbose
    ) { frame, surface, dimensions, bindGroup, cube, composeFrame, app ->
        val ctx = frame.ctx
        checkForFrameDrops(ctx, frame.deltaMs)


        val commandEncoder = ctx.device.createCommandEncoder()

        val textureView = frame.windowTexture
        val renderPassDescriptor = RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = dimensions.msaaTextureView,
                    resolveTarget = textureView,
                    clearValue = Color(0.0, 0.0, 0.0, 0.0),
                    loadOp = GPULoadOp.Clear,
                    storeOp = GPUStoreOp.Discard
                ),
            ),
            depthStencilAttachment = RenderPassDepthStencilAttachment(
                view = dimensions.depthStencilView,
                depthClearValue = 1.0f,
                depthLoadOp = GPULoadOp.Clear,
                depthStoreOp = GPUStoreOp.Store
            ),
        )
        val now = (System.currentTimeMillis() % 1000 * 2 * PI.toFloat()) / 1000f

//        val lookAt = appLifecycle.assertValue

        val viewProjection = dimensions.projection * app.camera.matrix


        ctx.device.queue.writeBuffer(
            surface.uniformBuffer,
            0uL,
            // viewProjection, cameraPos, lightPos
            viewProjection.array + app.camera.position.toAlignedArray() + lightPos.toAlignedArray()
                    + floatArrayOf(dimensions.dims.width.toFloat(), dimensions.dims.height.toFloat())
        )

        //TODO: that didn't solve it wtf. Clue: the lighting should be uniform across the entire face, but it's actually changing.
        fun Mat4f.andNormal() = this.array + Mat3f.fromMat4(this).inverse().transpose().array

//        ctx.device.queue.writeBuffer(
//            surface.storageBuffer,
//            0uL,
//            Mat4f.translation(Vec3f.zero()).andNormal() + Color.White.toFloatArray() // Add white color
//        )
//
//        val instanceStride = surface.instanceStride
//
//
//        ctx.device.queue.writeBuffer(
//            surface.storageBuffer,
//            instanceStride,
//            Mat4f.translation(Vec3f.zero())
//                .scale(Vec3f(x = 10f, y = 0.1f, z = 0.1f))
//                .andNormal() + Color.Red.toFloatArray() // X axis
//        )
//
//        ctx.device.queue.writeBuffer(
//            surface.storageBuffer,
//            instanceStride * 2u,
//            Mat4f.translation(Vec3f.zero())
//                .scale(Vec3f(x = 0.1f, y = 10f, z = 0.1f))
//                .andNormal() + Color.Green.toFloatArray() // Y Axis
//        )
//        ctx.device.queue.writeBuffer(
//            surface.storageBuffer,
//            instanceStride * 3u,
//            Mat4f.translation(Vec3f.zero())
//                .scale(Vec3f(x = 0.1f, y = 0.1f, z = 10f))
//                .andNormal() + Color.Blue.toFloatArray() // Z axis
//        )
//
//        ctx.device.queue.writeBuffer(
//            surface.storageBuffer,
//            instanceStride * 4u,
//            Mat4f.translation(Vec3f(0f, 0f, -1f))
//                .scale(Vec3f(x = 10f, y = 10f, z = 0.1f))
//                .andNormal() + Color.Gray.toFloatArray()
//        )
//
//        ctx.device.queue.writeBuffer(
//            surface.storageBuffer,
//            instanceStride * 5u,
//            Mat4f.translation(Vec3f(2f, 2f, 2f)).andNormal() + Color.White.toFloatArray() // Add white color
//        )
//
//        ctx.device.queue.writeBuffer(
//            surface.storageBuffer,
//            instanceStride * 6u,
//            Mat4f.translation(lightPos).uniformScale(0.2f).andNormal() + Color.White.toFloatArray() // Add white color
//        )
//
//        ctx.device.queue.writeBuffer(
//            surface.storageBuffer,
//            instanceStride * 7u,
//            Mat4f.translation(Vec3f(4f, -4f, 0.5f))
//                .andNormal() + Color.Gray.toFloatArray()
//        )


        val pass = commandEncoder.beginRenderPass(renderPassDescriptor)
//        pass.setPipeline(cube.pipeline)
        bindGroup.draw(pass)

//        pass.setBindGroup(0u, bindGroup.main)
//        pass.setBindGroup(1u, bindGroup.material) // Will need to be different for each material
//
//        pass.setVertexBuffer(0u, surface.sphere.vertexBuffer)
//        pass.setIndexBuffer(surface.sphere.indexBuffer, GPUIndexFormat.Uint32)
//        pass.drawIndexed(surface.sphereMesh.indexCount)
//        pass.drawIndexed(surface.sphereMesh.indexCount, instanceCount = 2u, firstInstance = 5u)
//
//        pass.setVertexBuffer(0u, surface.cube.vertexBuffer)
//        pass.setIndexBuffer(surface.cube.indexBuffer, GPUIndexFormat.Uint32)
//        pass.drawIndexed(surface.cubeMesh.indexCount.toUInt(), instanceCount = 4u, firstInstance = 1u)
//        pass.drawIndexed(surface.cubeMesh.indexCount.toUInt(), instanceCount = 1u, firstInstance = 7u)
//
//        pass.end()

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

//private fun Color.getBytes() = byteArrayOf(red.colorByte(), green.colorByte(), blue.colorByte(), alpha.colorByte())
private fun Color.toFloatArray() = floatArrayOf(red, green, blue, alpha)

private fun Float.colorByte() = (this * 255).roundToInt().toByte()

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
