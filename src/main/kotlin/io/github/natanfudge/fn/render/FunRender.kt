package io.github.natanfudge.fn.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.core.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.util.FunLogLevel
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.WindowCallbacks
import io.github.natanfudge.fn.window.WindowDimensions
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.github.natanfudge.wgpu4k.matrix.Vec4f
import io.ygdrasil.webgpu.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.PI
import kotlin.math.roundToInt

//TODO:
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


    override fun close() {
        closeAll(depthStencilView, depthTexture, msaaTextureView, msaaTexture)
    }
}

//TODO: reload -> shader change crasharinos...

class FunSurface(val ctx: WebGPUContext) : AutoCloseable {
    val cubeMesh = Mesh.UnitCube
    val cubeVerticesBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = cubeMesh.vertexCount * Vec3f.SIZE_BYTES, // x,y,z for each vertex, total 3 coords, each coord is a float so 4 bytes
            usage = setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
            mappedAtCreation = true
        )
    ).also {
        cubeMesh.vertices.array.writeInto(it.getMappedRange())
        it.unmap()
    }

    val cubeIndicesBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = cubeMesh.indexCount * Float.SIZE_BYTES.toULong(),
            usage = setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
            mappedAtCreation = true
        )
    ).also {
        it.mapFrom(cubeMesh.indices.array)
        it.unmap()
    }

    val sphereMesh = Mesh.sphere()

    val sphereIndicesBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = sphereMesh.indexCount * Float.SIZE_BYTES.toULong(),
            usage = setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
            mappedAtCreation = true
        )
    ).also {
        it.mapFrom(sphereMesh.indices.array)
        it.unmap()
    }

    val sphereVerticesBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = sphereMesh.vertexCount * Vec3f.SIZE_BYTES, // x,y,z for each vertex, total 3 coords, each coord is a float so 4 bytes
            usage = setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
            mappedAtCreation = true
        )
    ).also {
        sphereMesh.vertices.array.writeInto(it.getMappedRange())
        it.unmap()
    }


    val uniformBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = Mat4f.SIZE_BYTES.toULong(),// 4x4 matrix
            usage = setOf(GPUBufferUsage.Uniform, GPUBufferUsage.CopyDst)
        )
    )

    val maxInstances = 10uL

    val storageBuffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = (Mat4f.SIZE_BYTES + Vec4f.SIZE_BYTES) * maxInstances, // Include space for both model matrix and color
            usage = setOf(GPUBufferUsage.Storage, GPUBufferUsage.CopyDst)
        )
    )

    override fun close() {
        closeAll(cubeVerticesBuffer, cubeIndicesBuffer, sphereVerticesBuffer, sphereIndicesBuffer, uniformBuffer, storageBuffer)
    }
}

var pipelines = 0

class AppState {
    val camera = Camera()
    val input = InputManager(this)
}

class InputManager(val app: AppState) {
    private val heldKeys = mutableSetOf<Key>()
    val callbacks: WindowCallbacks = object : WindowCallbacks {
        override fun keyEvent(event: KeyEvent) {
            when (event.type) {
                KeyEventType.KeyDown -> heldKeys.add(event.key)
                KeyEventType.KeyUp -> heldKeys.remove(event.key)
            }
        }
    }

    fun poll() {
        heldKeys.forEach { whilePressed(it) }
    }

    fun whilePressed(key: Key) {
        val delta = 0.05f
        when (key) {
            Key.W -> app.camera.moveForward(delta)
            Key.S -> app.camera.moveBackward(delta)
            Key.A -> app.camera.moveLeft(delta)
            Key.D -> app.camera.moveRight(delta)
            Key.Spacebar -> app.camera.moveUp(delta)
            Key.CtrlLeft -> app.camera.moveDown(delta)
        }
    }
}

class Camera {
    val position = Vec3f(5f, 5f, 5f)
    val up = Vec3f(0f, 0f, 1f)

    /**
     * Always normalized
     */
    val lookDirection = (Vec3f.zero() - position).normalize()

    val lookAt = Mat4f()

    init {
        calculateLookAt()
    }

    //TODO: on app failure after reload, try restarting it
    private fun calculateLookAt() {
        Mat4f.lookAt(
            eye = position,
            target = position + lookDirection,
            up = up,
            dst = lookAt
        )
    }

    fun moveForward(delta: Float) {
        move(lookDirection, delta)
    }

    fun moveBackward(delta: Float) {
        moveForward(-delta)
    }

    fun moveLeft(delta: Float) {
        move(up.cross(lookDirection), delta)
    }

    fun moveRight(delta: Float) {
        moveLeft(-delta)
    }

    fun moveUp(delta: Float) {
        position.add(up * delta, position)
        calculateLookAt()
    }

    fun moveDown(delta: Float) {
        moveUp(-delta)
    }

    private fun move(direction: Vec3f, delta: Float) {
        val onlyXY = Vec3f(direction.x, direction.y, 0f)
        onlyXY.normalize(onlyXY)
        onlyXY.mulScalar(delta, onlyXY)
        position.add(onlyXY, position)
        // Don't move 'up' (z) with forward/back movement, Minecraft style
        calculateLookAt()
    }
}


@OptIn(ExperimentalAtomicApi::class)
fun WebGPUWindow.bindFunLifecycles(compose: ComposeWebGPURenderer, fsWatcher: FileSystemWatcher) {
    val appLifecycle = ProcessLifecycle.bind("App") {

        AppState()
    }

    val surfaceLifecycle = surfaceLifecycle.bind(appLifecycle, "Fun Surface") { surface, app ->
        surface.window.callbacks["Fun"] = app.input.callbacks
        FunSurface(surface)
    }
    val funDimLifecycle = dimensionsLifecycle.bind("Fun Dimensions") {
        FunFixedSizeWindow(it.surface, it.dimensions)
    }


    val cubeLifecycle = createReloadingPipeline(
        "Cube",
        this@bindFunLifecycles.surfaceLifecycle, fsWatcher,
        vertexShader = ShaderSource.HotFile("cube"),
//        fragmentShader = ShaderSource.HotFile("cube.fragment")
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
                        arrayStride = 4uL * 3uL, // 4floats × 3bytes  (12bytes per vertex)
                        attributes = listOf(
                            VertexAttribute(format = GPUVertexFormat.Float32x3, offset = 0uL, shaderLocation = 0u), // Position
                        )
                    ),
                )
            ),
            fragment = FragmentState(
                module = fragment,
                entryPoint = "fs_main",
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
            ),
            multisample = MultisampleState(count = msaaSamples)
        )
    }

    val bindGroupLifecycle = cubeLifecycle.bind(surfaceLifecycle, "Fun BindGroup") { pipeline, surface ->
        println("Using pipeline ${pipeline.pipeline.label}")
        surface.ctx.device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = BufferBinding(
                            buffer = surface.uniformBuffer
                        )
                    ),
                    BindGroupEntry(
                        binding = 1u,
                        resource = BufferBinding(
                            buffer = surface.storageBuffer
                        )
                    )
                )
            )
        )
    }


    // Running this in-frame is a bad idea since it can trigger a new frame
    window.eventPollLifecycle.bind("Fun Polling", FunLogLevel.Verbose) {
        if (HOT_RELOAD_SHADERS) {
            fsWatcher.poll()
        }
        appLifecycle.assertValue.input.poll()
    }

    frameLifecycle.bind(
        surfaceLifecycle, funDimLifecycle, bindGroupLifecycle, cubeLifecycle,
        compose.frameLifecycle, appLifecycle,
        "Fun Frame", FunLogLevel.Verbose
    ) { frame, surface, dimensions, bindGroup, cube, composeFrame, app ->
//        println("Running fun frame")
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

        val viewProjection = dimensions.projection * app.camera.lookAt

        ctx.device.queue.writeBuffer(
            surface.uniformBuffer,
            0uL,
            viewProjection.array
        )

        ctx.device.queue.writeBuffer(
            surface.storageBuffer,
            0uL,
            Mat4f.translation(Vec3f(-0.5f, -0.5f, -0.5f)).array + Color.White.toFloatArray() // Add white color
        )

        ctx.device.queue.writeBuffer(
            surface.storageBuffer,
            (Mat4f.SIZE_BYTES + Vec4f.SIZE_BYTES).toULong(),
            Mat4f.translation(Vec3f(0f, -0.05f, -0.05f))
                .scale(Vec3f(x = 10f, y = 0.1f, z = 0.1f))
                .array + Color.Red.toFloatArray()
        )

        ctx.device.queue.writeBuffer(
            surface.storageBuffer,
            (Mat4f.SIZE_BYTES + Vec4f.SIZE_BYTES).toULong() * 2u,
            Mat4f.translation(Vec3f(-0.05f, 0f, -0.05f))
                .scale(Vec3f(x = 0.1f, y = 10f, z = 0.1f))
                .array + Color.Green.toFloatArray()
        )
        ctx.device.queue.writeBuffer(
            surface.storageBuffer,
            (Mat4f.SIZE_BYTES + Vec4f.SIZE_BYTES).toULong() * 3u,
            Mat4f.translation(Vec3f(-0.05f, -0.05f, 0f))
                .scale(Vec3f(x = 0.1f, y = 0.1f, z = 10f))
                .array + Color.Blue.toFloatArray()
        )


        val pass = commandEncoder.beginRenderPass(renderPassDescriptor)
        pass.setPipeline(cube.pipeline)
        pass.setBindGroup(0u, bindGroup)

        pass.setVertexBuffer(0u, surface.sphereVerticesBuffer)
        pass.setIndexBuffer(surface.sphereIndicesBuffer, GPUIndexFormat.Uint32)
        pass.drawIndexed(surface.sphereMesh.indexCount.toUInt())

        pass.setVertexBuffer(0u, surface.cubeVerticesBuffer)
        pass.setIndexBuffer(surface.cubeIndicesBuffer, GPUIndexFormat.Uint32)
        pass.drawIndexed(surface.cubeMesh.indexCount.toUInt(), instanceCount = 3u, firstInstance = 1u)

        pass.end()

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