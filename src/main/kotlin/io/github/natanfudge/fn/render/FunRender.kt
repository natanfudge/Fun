package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.*
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.WindowDimensions
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

//TODO:
// 5. Rewrite Lifecycles to use a Tree/Graph thing
// 5.5 Figure out if there's a way to resolve hot reloading being bad with lambdas.
// I figured it out. It's probably because we have old lambdas stored in the graph, and it's trying to run them and failing.
// We need some way to evict the old lambdas and replace them but tbh I don't know if that is even possible. We might need to put everything in a special
// callback and rerun the callback whenever the code is reloaded.
// Fix error handling of wgpu!!
// 6. Start drawing basic objects:
//   A. An origin marker
//   B. XYZ axis arrows
// 7. Figure out how to draw spheres
// 8. Draw a sphere for the origin marker and the arrows
// 9. Implement WASD camera
// 10. Implement orbital camera
// 11. Integrate the switch between both cameras
// 12. Material rendering system:
//      A. Start by having only a mesh
//      B. Then add texturing
//      C. Then add a normal map
//      D. then add phong lighting
//      E. then add the other PBR things
// 13. Ray casting & selection
// 14. Physics
// 15. Trying making a basic game
// 16. Integrate the Fun "ECS" system and allow assigning physicality to components, allowing you to click on things and view their state

class FunFixedSizeWindow(device: GPUDevice, val dims: WindowDimensions) : AutoCloseable {
    // Create z buffer
    val depthTexture = device.createTexture(
        TextureDescriptor(
            size = Extent3D(dims.width.toUInt(), dims.height.toUInt()),
            format = GPUTextureFormat.Depth24Plus,
            usage = setOf(GPUTextureUsage.RenderAttachment)
        )
    )
    val depthStencilView = depthTexture.createView()

//    var x = 2

    override fun close() {
        closeAll(depthStencilView, depthTexture)
    }
}


class FunSurface(val ctx: WebGPUContext) : AutoCloseable {

    val cubeMesh = Mesh.UnitCube


    val verticesBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = cubeMesh.vertexCount * Vec3f.SIZE_BYTES, // x,y,z for each vertex, total 3 coords, each coord is a float so 4 bytes
            usage = setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
            mappedAtCreation = true
        )
    )

    val indicesBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = cubeMesh.indexCount * Float.SIZE_BYTES.toULong(),
            usage = setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
            mappedAtCreation = true
        )
    )

    val uniformBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = Mat4f.SIZE_BYTES.toULong(),// 4x4 matrix
            usage = setOf(GPUBufferUsage.Uniform, GPUBufferUsage.CopyDst)
        )
    )
    val viewProjection = Mat4f.identity()

    init {
        verticesBuffer.mapFrom(cubeMesh.vertices.array)
        verticesBuffer.unmap()

        indicesBuffer.mapFrom(cubeMesh.indices.array)
        indicesBuffer.unmap()
    }



    override fun close() {
        closeAll(verticesBuffer, indicesBuffer, uniformBuffer)
    }
}



@OptIn(ExperimentalAtomicApi::class)
fun WebGPUWindow.bindFunLifecycles(compose: ComposeWebGPURenderer, fsWatcher: FileSystemWatcher) {


    val surfaceLifecycle = this@bindFunLifecycles.surfaceLifecycle.bind("Fun Surface") {
        FunSurface(it)
    }
    val funDimLifecycle = dimensionsLifecycle.bind("Fun Dimensions") {
        FunFixedSizeWindow(it.surface.device, it.dimensions)
    }

    val cubeLifecycle = createReloadingPipeline(
        "Cube",
        this@bindFunLifecycles.surfaceLifecycle, fsWatcher,
        vertexShader = ShaderSource.HotFile("cube.vertex"),
        fragmentShader = ShaderSource.HotFile("cube.fragment")
    ) { vertex, fragment ->
        RenderPipelineDescriptor(
            vertex = VertexState(
                module = vertex,
                entryPoint = "main",
                buffers = listOf(
                    VertexBufferLayout(
                        arrayStride = 4uL * 3uL, // 4floats × 3bytes  (12bytes per vertex)
                        attributes = listOf(
                            VertexAttribute(format = GPUVertexFormat.Float32x3, offset = 0uL, shaderLocation = 0u), // Position
                        )
                    ),
                )
            ),
            fragment = FragmentState(
                module = fragment,
                entryPoint = "main",
                targets = listOf(ColorTargetState(presentationFormat))
            ),
            primitive = PrimitiveState(
                topology = GPUPrimitiveTopology.TriangleList,
                cullMode = GPUCullMode.Back
            ),
            depthStencil = DepthStencilState(
                format = GPUTextureFormat.Depth24Plus,
                depthWriteEnabled = true,
                depthCompare = GPUCompareFunction.Less
            )
        )
    }

    val bindGroupLifecycle = cubeLifecycle.bind(surfaceLifecycle,"Fun BindGroup") { pipeline, surface ->
        surface.ctx.device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = BufferBinding(
                            buffer = surface.uniformBuffer
                        )
                    )
                )
            )
        )
    }


//    val cube by cubeLifecycle

    val x = 1 to 2



    frameLifecycle.bind(
        surfaceLifecycle,funDimLifecycle, bindGroupLifecycle,cubeLifecycle,
        compose.frameLifecycle,
        "Fun Frame", FunLogLevel.Verbose
    ) { frame, surface, dimensions, bindGroup,cube, composeFrame ->
//        println(x.first)
        val ctx = frame.ctx
        checkForFrameDrops(ctx, frame.deltaMs)

        if (HOT_RELOAD_SHADERS) {
            fsWatcher.poll()
        }
        val commandEncoder = ctx.device.createCommandEncoder().ac

        val textureView = frame.windowFrame.texture.createView().ac
        val renderPassDescriptor = RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = textureView,
                    clearValue = Color(0.0, 0.0, 0.0, 0.0),
                    loadOp = GPULoadOp.Clear,
                    storeOp = GPUStoreOp.Store
                ),
            ),
            depthStencilAttachment = RenderPassDepthStencilAttachment(
                view = dimensions.depthStencilView,
                depthClearValue = 1.0f,
                depthLoadOp = GPULoadOp.Clear,
                depthStoreOp = GPUStoreOp.Store
            )
        )
        val now = (System.currentTimeMillis() % 1000 * 2 * PI.toFloat()) / 1000f
        Mat4f.identity(surface.viewProjection)
        surface.viewProjection.rotate(Vec3f(sin(now), cos(now), 0f), 1f, surface.viewProjection)

        ctx.device.queue.writeBuffer(
            surface.uniformBuffer,
            0uL,
            surface.viewProjection.array
        )


        val pass = commandEncoder.beginRenderPass(renderPassDescriptor)
        pass.setPipeline(cube.pipeline)
        pass.setBindGroup(0u, bindGroup)
        pass.setVertexBuffer(0u, surface.verticesBuffer)
        pass.setIndexBuffer(surface.indicesBuffer, GPUIndexFormat.Uint32)
        pass.drawIndexed(surface.cubeMesh.indexCount.toUInt())
        pass.end()


        compose.frame(commandEncoder, textureView, composeFrame)

        ctx.device.queue.submit(listOf(commandEncoder.finish()));
        ctx.context.present()
    }

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