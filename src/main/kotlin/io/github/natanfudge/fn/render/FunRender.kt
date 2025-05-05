package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.StatefulLifecycle
import io.github.natanfudge.fn.util.bindCloseable
import io.github.natanfudge.fn.util.bindState
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.RepeatingWindowCallbacks
import io.github.natanfudge.fn.window.WindowDimensions
import io.github.natanfudge.fn.window.combine
import io.github.natanfudge.wgpu4k.matrix.Mat4
import io.github.natanfudge.wgpu4k.matrix.Vec3
import io.ygdrasil.webgpu.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

//TODO:
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
        depthStencilView.close()
        depthTexture.close()
        val x = 2
    }
}

//TODO: we get this error on hot reload, if we look at the lifecycle logs it should be possible to figure it out.
// thread '<unnamed>' panicked at C:\Users\runneradmin\.cargo\git\checkouts\wgpu-045f9a3b3e40a5c0\8a38f5f\wgpu-core\src\storage.rs:130:9:
//assertion `left == right` failed: Surface[Id(0,1)] is no longer alive
//  left: 1
// right: 2
//note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace
fun WebGPUWindow.bindFunLifecycles() {
    val fixedSizeValue by dimensionsLifecycle.bindState("Fun fixed size window") {
        FunFixedSizeWindow(surfaceLifecycle.assertValue.device, this)
    }
    //TODO: better autoclose solution
    surfaceLifecycle.bindCloseable { ctx->
        val closeable = AutoCloseImpl()
        with(closeable){
            val fsWatcher = FileSystemWatcher()
//        compose.init(device, this, fsWatcher, presentationFormat)

            val cubeMesh = Mesh.UnitCube

//        val fixedSizeValue by dimensionsLifecycle

            val cube = ReloadingPipeline(
                ctx.device, fsWatcher,
                vertexShader = ShaderSource.HotFile("cube.vertex"),
                fragmentShader = ShaderSource.HotFile("cube.fragment"),
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
                        targets = listOf(ColorTargetState(ctx.presentationFormat))
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
            }.ac
            val verticesBuffer = ctx.device.createBuffer(
                BufferDescriptor(
                    size = cubeMesh.vertexCount * 4uL * 3uL, // x,y,z for each vertex, total 3 coords, each coord is a float so 4 bytes
                    usage = setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
                    mappedAtCreation = true
                )
            ).ac



            verticesBuffer.mapFrom(cubeMesh.vertices.array)
            verticesBuffer.unmap()

            val indicesBuffer = ctx.device.createBuffer(
                BufferDescriptor(
                    size = cubeMesh.indexCount * 4uL,
                    usage = setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
                    mappedAtCreation = true
                )
            ).ac

            indicesBuffer.mapFrom(cubeMesh.indices.array)
            indicesBuffer.unmap()

            val uniformBuffer = ctx.device.createBuffer(
                BufferDescriptor(
                    size = 64uL,// 4x4 matrix
                    usage = setOf(GPUBufferUsage.Uniform, GPUBufferUsage.CopyDst)
                )
            ).ac
            val viewProjection = Mat4.identity()

            val frameAutoclose = AutoCloseImpl()
            val funFrame = frameLifecycle.bindCloseable("Fun frame") { deltaMs ->
                with(frameAutoclose) {
                    // Interestingly, this call (context.getCurrentTexture()) invokes VSync (so it stalls here usually)
                    val windowFrame = ctx.context.getCurrentTexture()
                        .also { it.texture.ac } // Close texture

                    checkForFrameDrops(this@bindFunLifecycles, deltaMs)

                    if (HOT_RELOAD_SHADERS) {
                        fsWatcher.poll()
                    }
                    val commandEncoder = ctx.device.createCommandEncoder().ac

                    val textureView = windowFrame.texture.createView().ac
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
                            view = fixedSizeValue.depthStencilView,
                            depthClearValue = 1.0f,
                            depthLoadOp = GPULoadOp.Clear,
                            depthStoreOp = GPUStoreOp.Store
                        )
                    )
                    val now = (System.currentTimeMillis() % 1000 * 2 * PI.toFloat()) / 1000f
                    Mat4.identity(viewProjection)
                    viewProjection.rotate(Vec3(sin(now), cos(now), 0f), 1f, viewProjection)

                    ctx.device.queue.writeBuffer(
                        uniformBuffer,
                        0uL,
                        viewProjection.array
                    )

                    //TODO: this should be in the pipeline lifecycle
                    val uniformBindGroup = ctx.device.createBindGroup(
                        BindGroupDescriptor(
                            layout = cube.pipeline.getBindGroupLayout(0u),
                            entries = listOf(
                                BindGroupEntry(
                                    binding = 0u,
                                    resource = BufferBinding(
                                        buffer = uniformBuffer
                                    )
                                )
                            )
                        )
                    ).ac


                    val pass = commandEncoder.beginRenderPass(renderPassDescriptor)
                    pass.setPipeline(cube.pipeline)
                    pass.setBindGroup(0u, uniformBindGroup)
                    pass.setVertexBuffer(0u, verticesBuffer)
                    pass.setIndexBuffer(indicesBuffer, GPUIndexFormat.Uint32)
                    pass.drawIndexed(cubeMesh.indexCount.toUInt())
                    pass.end()

//            compose.frame(device, this, commandEncoder, textureView)


                    ctx.device.queue.submit(listOf(commandEncoder.finish()));

                };
                return@bindCloseable {
                    frameAutoclose.close()
                }
            };
            { // Remove old frameLifecycle when surface is deleted
                frameLifecycle.unbind(funFrame)
                closeable.close()
            }
        }

    }
}
//
//
//fun WebGPUContext.funRender(
//    window: WebGPUWindow,
//    compose: ComposeWebGPURenderer,
//    dimensionsLifecycle: StatefulLifecycle<WindowDimensions, FunFixedSizeWindow>,
//): RepeatingWindowCallbacks {
//    val fsWatcher = FileSystemWatcher()
//    compose.init(device, this, fsWatcher, presentationFormat)
//
//    val cubeMesh = Mesh.UnitCube
//
//    val fixedSizeValue by dimensionsLifecycle
//
//
//    val cube = ReloadingPipeline(
//        device, fsWatcher,
//        vertexShader = ShaderSource.HotFile("cube.vertex"),
//        fragmentShader = ShaderSource.HotFile("cube.fragment"),
//    ) { vertex, fragment ->
//        RenderPipelineDescriptor(
//            vertex = VertexState(
//                module = vertex,
//                entryPoint = "main",
//                buffers = listOf(
//                    VertexBufferLayout(
//                        arrayStride = 4uL * 3uL, // 4floats × 3bytes  (12bytes per vertex)
//                        attributes = listOf(
//                            VertexAttribute(format = GPUVertexFormat.Float32x3, offset = 0uL, shaderLocation = 0u), // Position
//                        )
//                    ),
//                )
//            ),
//            fragment = FragmentState(
//                module = fragment,
//                entryPoint = "main",
//                targets = listOf(ColorTargetState(presentationFormat))
//            ),
//            primitive = PrimitiveState(
//                topology = GPUPrimitiveTopology.TriangleList,
//                cullMode = GPUCullMode.Back
//            ),
//            depthStencil = DepthStencilState(
//                format = GPUTextureFormat.Depth24Plus,
//                depthWriteEnabled = true,
//                depthCompare = GPUCompareFunction.Less
//            )
//        )
//    }.ac
//    val verticesBuffer = device.createBuffer(
//        BufferDescriptor(
//            size = cubeMesh.vertexCount * 4uL * 3uL, // x,y,z for each vertex, total 3 coords, each coord is a float so 4 bytes
//            usage = setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
//            mappedAtCreation = true
//        )
//    ).ac
//
//
//
//    verticesBuffer.mapFrom(cubeMesh.vertices.array)
//    verticesBuffer.unmap()
//
//    val indicesBuffer = device.createBuffer(
//        BufferDescriptor(
//            size = cubeMesh.indexCount * 4uL,
//            usage = setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
//            mappedAtCreation = true
//        )
//    ).ac
//
//    indicesBuffer.mapFrom(cubeMesh.indices.array)
//    indicesBuffer.unmap()
//
//    val uniformBuffer = device.createBuffer(
//        BufferDescriptor(
//            size = 64uL,// 4x4 matrix
//            usage = setOf(GPUBufferUsage.Uniform, GPUBufferUsage.CopyDst)
//        )
//    ).ac
//    val viewProjection = Mat4.identity()
//
//
//    return compose.callbacks.combine(object : RepeatingWindowCallbacks {
//        override fun AutoClose.frame(deltaMs: Double) {
//            // Interestingly, this call (context.getCurrentTexture()) invokes VSync (so it stalls here usually)
//            val windowFrame = context.getCurrentTexture()
//                .also { it.texture.ac } // Close texture
//
//            checkForFrameDrops(window, deltaMs)
//
//            if (HOT_RELOAD_SHADERS) {
//                fsWatcher.poll()
//            }
//            val commandEncoder = device.createCommandEncoder().ac
//
//            val textureView = windowFrame.texture.createView().ac
//            val renderPassDescriptor = RenderPassDescriptor(
//                colorAttachments = listOf(
//                    RenderPassColorAttachment(
//                        view = textureView,
//                        clearValue = Color(0.0, 0.0, 0.0, 0.0),
//                        loadOp = GPULoadOp.Clear,
//                        storeOp = GPUStoreOp.Store
//                    ),
//                ),
//                depthStencilAttachment = RenderPassDepthStencilAttachment(
//                    view = fixedSizeValue.depthStencilView,
//                    depthClearValue = 1.0f,
//                    depthLoadOp = GPULoadOp.Clear,
//                    depthStoreOp = GPUStoreOp.Store
//                )
//            )
//            val now = (System.currentTimeMillis() % 1000 * 2 * PI.toFloat()) / 1000f
//            Mat4.identity(viewProjection)
//            viewProjection.rotate(Vec3(sin(now), cos(now), 0f), 1f, viewProjection)
//
//            device.queue.writeBuffer(
//                uniformBuffer,
//                0uL,
//                viewProjection.array
//            )
//
//            //TODO: this should be in the pipeline lifecycle
//            val uniformBindGroup = device.createBindGroup(
//                BindGroupDescriptor(
//                    layout = cube.pipeline.getBindGroupLayout(0u),
//                    entries = listOf(
//                        BindGroupEntry(
//                            binding = 0u,
//                            resource = BufferBinding(
//                                buffer = uniformBuffer
//                            )
//                        )
//                    )
//                )
//            ).ac
//
//
//            val pass = commandEncoder.beginRenderPass(renderPassDescriptor)
//            pass.setPipeline(cube.pipeline)
//            pass.setBindGroup(0u, uniformBindGroup)
//            pass.setVertexBuffer(0u, verticesBuffer)
//            pass.setIndexBuffer(indicesBuffer, GPUIndexFormat.Uint32)
//            pass.drawIndexed(cubeMesh.indexCount.toUInt())
//            pass.end()
//
//            //TODO: proper lifecycles might resolve my hot reloading issues...
//
//            compose.frame(device, this, commandEncoder, textureView)
//
//
//            device.queue.submit(listOf(commandEncoder.finish()))
//        }
//    })
//}

private fun checkForFrameDrops(window: WebGPUWindow, deltaMs: Double) {
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