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

//TODO: 1. Fix reloading failing:
//// Caused by:
////  In wgpuDeviceCreateRenderPipeline, label = 'Compose Pipeline'
////    Error matching ShaderStages(FRAGMENT) shader requirements against the pipeline
////      Unable to find entry point 'fs_main'
// 2. Test hot reloading
// 3. Setup lambda cleanup on reload
// 4. Start catching wgpu errors with new api



@OptIn(ExperimentalAtomicApi::class)
fun WebGPUWindow.bindFunLifecycles(compose: ComposeWebGPURenderer, fsWatcher: FileSystemWatcher) {
    val sizedWindow by dimensionsLifecycle.bind("Fun fixed sized window") {
        FunFixedSizeWindow(it.surface.device, it.dimensions)
    }

    val surface by surfaceLifecycle.bind("Fun Surface") {
        FunSurface(it)
    }

    val cubeLifecycle = createReloadingPipeline(
        "Cube",
        surfaceLifecycle, fsWatcher,
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

    val uniformBindGroup by cubeLifecycle.bind("Fun BindGroup") {
        it.ctx.device.createBindGroup(
            BindGroupDescriptor(
                layout = it.pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = BufferBinding(
                            buffer = surface.uniformBuffer // HACK: this has a good chance of failing if we reload the wrong part of the lifecycle tree,
                            // since we didn't explicitly depend on the fun surface's lifecycle.
                        )
                    )
                )
            )
        )
    }


    val cube by cubeLifecycle




    frameLifecycle.bind("Fun Frame", FunLogLevel.Verbose) { frame ->
        with(surface) {

//            // Interestingly, this call (context.getCurrentTexture()) invokes VSync (so it stalls here usually)
//            val windowFrame = ctx.context.getCurrentTexture()
//                .also { it.texture.ac } // Close texture

//            println(x + 10)

//            println(x)

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
                    view = sizedWindow.depthStencilView,
                    depthClearValue = 1.0f,
                    depthLoadOp = GPULoadOp.Clear,
                    depthStoreOp = GPUStoreOp.Store
                )
            )
            val now = (System.currentTimeMillis() % 1000 * 2 * PI.toFloat()) / 1000f
            Mat4f.identity(viewProjection)
            viewProjection.rotate(Vec3f(sin(now), cos(now), 0f), 1f, viewProjection)

            ctx.device.queue.writeBuffer(
                uniformBuffer,
                0uL,
                viewProjection.array
            )


            val pass = commandEncoder.beginRenderPass(renderPassDescriptor)
            pass.setPipeline(cube.pipeline)
            pass.setBindGroup(0u, uniformBindGroup)
            pass.setVertexBuffer(0u, verticesBuffer)
            pass.setIndexBuffer(indicesBuffer, GPUIndexFormat.Uint32)
            pass.drawIndexed(cubeMesh.indexCount.toUInt())
            pass.end()

            compose.frame( commandEncoder, textureView)

            ctx.device.queue.submit(listOf(commandEncoder.finish()));
        }

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