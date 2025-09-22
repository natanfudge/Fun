package io.github.natanfudge.fn.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunContextRegistry
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.core.InvalidationKey
import io.github.natanfudge.fn.core.exposeAsService
import io.github.natanfudge.fn.core.logWarn
import io.github.natanfudge.fn.core.serviceKey
import io.github.natanfudge.fn.network.ColorSerializer
import io.github.natanfudge.fn.render.utils.*
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.ReloadingPipeline
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.WebGPUSurfaceHolder
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.*
import korlibs.time.seconds
import korlibs.time.times
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.time.Duration

private val alignmentBytes = 16u
internal fun ULong.wgpuAlign(): ULong {
    val leftOver = this % alignmentBytes
    return if (leftOver == 0uL) this else ((this / alignmentBytes) + 1u) * alignmentBytes
}

internal fun UInt.wgpuAlignInt(): UInt = toULong().wgpuAlign().toUInt()


val lightPos get() = Vec3f(-2f, -2f, 101f)

/**
 *   initialTransform, normalMatrix, tint.color, tint.strength, if (model.image == null) 0 else 1
 */
object BaseInstanceData : Struct6<Mat4f, Mat3f, Color, Float, Int, Int, BaseInstanceData>(Mat4fDT, Mat3fDT, ColorDT, FloatDT, IntDT, IntDT)

/**
 * Stores the information specific to an instance, with a size that changes between each model
 *
 * In contrast, [BaseInstanceData] stores information per instance that is constant in size
 *
 *  */
/////////////////                       joint_matrices
class JointMatrix(jointCount: Int) : Struct1<List<Mat4f>, JointMatrix>(Mat4fArrayDT(jointCount))


object WorldUniform : Struct5<Mat4f, Vec3f, Vec3f, UInt, UInt, WorldUniform>(
    Mat4fDT, Vec3fDT, Vec3fDT, UIntDT, UIntDT
)


fun IntSize.toExtent3D(depthOrArrayLayers: Int = 1) = Extent3D(width.toUInt(), height = height.toUInt(), depthOrArrayLayers.toUInt())

val msaaSamples = 4u

class WorldRendererWindowSizeEffect(size: IntSize, ctx: WebGPUContext) : InvalidationKey() {
    val extent = size.toExtent3D()


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
    val depthStencilView = depthTexture.createView(TextureViewDescriptor(label = "MSAA Texture View"))

    val msaaTexture = ctx.device.createTexture(
        TextureDescriptor(
            size = extent,
            sampleCount = msaaSamples,
            format = ctx.presentationFormat,
            usage = setOf(GPUTextureUsage.RenderAttachment),
            label = "MSAA Texture"
        )
    )

    val msaaTextureView = msaaTexture.createView(TextureViewDescriptor(label = "MSAA Texture View"))

    override fun close() {
        closeAll(depthTexture, depthStencilView, msaaTexture, msaaTextureView)
    }
}

private var bindGroupIndex = 0

class WorldRendererSurfaceEffect(val ctx: WebGPUContext) : InvalidationKey() {

    val uniformBuffer = WorldUniform.createBuffer(ctx, 1u, expandable = false, GPUBufferUsage.Uniform)


    val vertexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 100_000_000u, expandable = true, GPUBufferUsage.Vertex)
    val indexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 20_000_000u, expandable = true, GPUBufferUsage.Index)

    val models = mutableMapOf<ModelId, BoundModel>()
    val rayCasting = RayCastingCache<RenderInstance>()

    internal val baseInstanceData: IndirectInstanceBuffer = IndirectInstanceBuffer(
        ctx, models, maxInstances = 50_000, expectedElementSize = BaseInstanceData.size
    ) { it.renderId }
    internal val jointMatrixData: IndirectInstanceBuffer =
        IndirectInstanceBuffer(ctx, models, maxInstances = 1_000, expectedElementSize = Mat4f.SIZE_BYTES * 50u) {
            (it.jointMatricesPointer.address / Mat4f.SIZE_BYTES).toInt()
        }
    val sampler = ctx.device.createSampler(
        SamplerDescriptor(
            label = "Fun Sampler",

            //TO DO: These make world panels look better (esp maxAnisotropy = 8), but are not needed for normal textures and is probably very expensive.
            // These should only apply to world panels.
            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear,
            mipmapFilter = GPUMipmapFilterMode.Linear,
            maxAnisotropy = 8u
        ),
    )

    fun createBindGroup(pipeline: GPURenderPipeline) = ctx.device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = listOf(
                BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniformBuffer.buffer)),
                BindGroupEntry(binding = 1u, resource = sampler),
                BindGroupEntry(binding = 2u, resource = BufferBinding(baseInstanceData.instanceBuffer.buffer)),
                BindGroupEntry(binding = 3u, resource = BufferBinding(baseInstanceData.instanceIndexBuffer.buffer)),
                BindGroupEntry(binding = 4u, resource = BufferBinding(jointMatrixData.instanceBuffer.buffer)),
                BindGroupEntry(binding = 5u, resource = BufferBinding(jointMatrixData.instanceIndexBuffer.buffer)),
            ),
            label = "World BindGroup #${bindGroupIndex++}"
        )
    )

    override fun close() {
        closeAll(
            vertexBuffer, indexBuffer, uniformBuffer, baseInstanceData, jointMatrixData
        )
        models.forEach { it.value.close() }
    }
}

val IntSize.aspectRatio get() = width.toFloat() / height
val IntSize.isEmpty get() = width == 0 || height == 0

var rendererNextIndex = 0

data class BeforeSubmitWebGPUDrawEvent(
    val encoder: GPUCommandEncoder, val drawTarget: GPUTextureView,
)


class WorldRenderer(val surfaceHolder: WebGPUSurfaceHolder) : Fun("WorldRenderer") {
    companion object {
        val service = serviceKey<WorldRenderer>()
    }
    val surfaceBinding by cached(surfaceHolder.surface) {
        // Recreated on every surface change
        WorldRendererSurfaceEffect(surfaceHolder.surface)
    }


    val windowSize get() = surfaceHolder.size

    val index = rendererNextIndex++

    var sizeBinding by cached(surfaceBinding) {
        WorldRendererWindowSizeEffect(surfaceHolder.size, surfaceHolder.surface)
    }

    init {
        exposeAsService(service)
        // I prefer manually reconstructing the WorldRendererWindowSizeEffect on window size change instead of
        // having the size as an invalidation key and refreshing the app, because this way the app doesn't get refreshed
        // constantly when the window is resized. I try to keep refreshes to be a dev-only reload thing, and not be a thing
        // the user experiences all the time like when he resizes the window.
        events.windowResize.listen { (size) ->
            if (!size.isEmpty) {
                sizeBinding = WorldRendererWindowSizeEffect(size, surfaceHolder.surface)
            }
        }
    }

    val pipelineHolder = ReloadingPipeline(
        "Object",
        surfaceHolder,
        vertexSource = ShaderSource.HotFile("object"),
    ) { vertex, fragment ->
        RenderPipelineDescriptor(
            label = "Fun Object Pipeline",
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
    var fovYRadians = PI.toFloat() / 3f
        set(value) {
            field = value
            this.projection = calculateProjectionMatrix()
        }


    var projection = calculateProjectionMatrix()

    var selectedObjectId: Int = -1

    var hoveredObject: Boundable? by funValue(null)

    var _camera = DefaultCamera()

    var bindgroup: GPUBindGroup by cached(surfaceBinding) {
        surfaceBinding.createBindGroup(pipelineHolder.pipeline)
    }


    val beforeSubmitDraw by event<BeforeSubmitWebGPUDrawEvent>()


    init {
        pipelineHolder.pipelineLoaded.listen { pipeline ->
            bindgroup = surfaceBinding.createBindGroup(pipeline)
            surfaceBinding.models.forEach { it.value.recreateBindGroup(pipeline) }
        }

        events.windowResize.listen { (size) ->
            projection = calculateProjectionMatrix(size)
        }

        events.frame.listen { delta ->
            val ctx = surfaceHolder.surface
            if (ctx.size.isEmpty) return@listen
            checkForFrameDrops(ctx, delta)
            val encoder = ctx.device.createCommandEncoder()

            // This call (context.getCurrentTexture()) invokes VSync (so it stalls here usually)
            // It's important to call this here and not nearby any user code, as the thread will spend a lot of time here,
            // and so if user code both calls this method and changes something, they are at great risk of a crash on DCEVM reload, see
            // https://github.com/JetBrains/JetBrainsRuntime/issues/534
            val underlyingWindowFrame = ctx.surface.getCurrentTexture()
            val windowTexture =
                underlyingWindowFrame.texture.createView(descriptor = TextureViewDescriptor(label = "Texture created directly from device ${ctx.index}"))

            val viewProjection = projection * _camera.viewMatrix

            val dimensions = surfaceHolder.size

            // Update selected object based on ray casting
            val rayCast = surfaceBinding.rayCasting.rayCast(getCursorRay(_camera, cursorPosition, viewProjection, dimensions))
            hoveredObject = rayCast?.value
            selectedObjectId = rayCast?.renderId ?: -1

            val uniform = WorldUniform(
                viewProjection, _camera.position, lightPos,
                dimensions.width.toUInt(), dimensions.height.toUInt()
            )


            surfaceBinding.uniformBuffer[GPUPointer(0u)] = uniform

            check(!sizeBinding.invalid)

            val renderPassDescriptor = RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = sizeBinding.msaaTextureView,
                        resolveTarget = windowTexture,
                        clearValue = Color(0.0, 0.0, 0.0, 0.0),
                        loadOp = GPULoadOp.Clear,
                        storeOp = GPUStoreOp.Discard
                    ),
                ),
                depthStencilAttachment = RenderPassDepthStencilAttachment(
                    view = sizeBinding.depthStencilView,
                    depthClearValue = 1.0f,
                    depthLoadOp = GPULoadOp.Clear,
                    depthStoreOp = GPUStoreOp.Store
                ),
            )


            val pass = encoder.beginRenderPass(renderPassDescriptor)

            surfaceBinding.baseInstanceData.rebuild()
            surfaceBinding.jointMatrixData.rebuild()
            check(pipelineHolder.valid)
//            check(windowTexture)
            pass.setPipeline(pipelineHolder.pipeline)

            pass.setBindGroup(0u, bindgroup)
            pass.setVertexBuffer(0u, surfaceBinding.vertexBuffer.buffer)
            pass.setIndexBuffer(surfaceBinding.indexBuffer.buffer, GPUIndexFormat.Uint32)

            var instanceIndex = 0u
            for (model in surfaceBinding.models.values) {
                val bindGroup = model.bindGroup
                if (bindGroup != null) {
                    for (instance in model.instances) {
                        instance.value.updateGPU()
                    }

                    pass.setBindGroup(1u, bindGroup)
                    val instances = model.instances.size.toUInt()
                    pass.drawIndexed(
                        model.data.mesh.indexCount,
                        instanceCount = instances,
                        firstIndex = model.firstIndex,
                        baseVertex = model.baseVertex,
                        firstInstance = instanceIndex
                    )
                    instanceIndex += instances
                } else {
                    logWarn("WorldRenderer"){"Skipping model because its bindgroup is null, not sure if this will work correctly"}
                }

            }
            pass.end()

            val err = ctx.error
            if (err != null) {
                ctx.error = null
                throw err
            }
            beforeSubmitDraw(BeforeSubmitWebGPUDrawEvent(encoder, windowTexture))

            ctx.device.queue.submit(listOf(encoder.finish()));

            ctx.surface.present()

            windowTexture.close()
            underlyingWindowFrame.texture.close()
            encoder.close()

            // On some driver versions if we don't do this then it will block on the vsync queue only when the queue is empty rather than what is full,
            // causing an inconsistent block cadence, 0/0/3 instead of 1/1/1/1/1
            runBlocking { ctx.device.poll() }

        }
    }


    private fun calculateProjectionMatrix(size: IntSize = surfaceHolder.size) = Mat4f.perspective(
        fieldOfViewYInRadians = fovYRadians,
        aspect = size.aspectRatio,
        zNear = 0.01f,
        zFar = 100f
    )


    /**
     * Returns where the use is pointing at in world space
     */
    private fun getCursorRay(camera: DefaultCamera, cursorPosition: Offset?, viewProjection: Mat4f, dimensions: IntSize): Ray {
        if (cursorPosition != null) {
            val ray = Selection.orbitalSelectionRay(
                cursorPosition,
                IntSize(dimensions.width, dimensions.height),
                viewProjection
            )
            return ray
        } else {
            return Ray(camera.position, camera.forward)
        }
    }

    var cursorPosition: Offset? = null


    fun getOrBindModel(model: Model): BoundModel {
        val existingModel = surfaceBinding.models[model.id]
        // This model != existingModel check makes sure the model is rebound in case it was changed in dev. This check is expensive, so we only do it in dev.
        if (existingModel == null || (Fun.DEV && model.material.texture?.path != existingModel.currentTexture?.path)) {
            surfaceBinding.models[model.id] = bind(model)
        }
        return surfaceBinding.models[model.id]!!
    }

    /**
     * Note: [models] is updated by [getOrBindModel]
     */
    private fun bind(model: Model): BoundModel {
        val vertexPointer = surfaceBinding.vertexBuffer.new(model.mesh.vertices.array)
        val indexPointer = surfaceBinding.indexBuffer.new(model.mesh.indices.array)
        val bound = BoundModel(
            model, surfaceHolder.surface,
            // These values are indices, not bytes, so we need to divide by the size of the value
            firstIndex = (indexPointer.address / Int.SIZE_BYTES.toUInt()).toUInt(),
            baseVertex = (vertexPointer.address / VertexArrayBuffer.StrideBytes).toInt(),
            pipeline = { pipelineHolder.pipeline },
        )

        return bound
    }


    fun spawn(id: FunId, model: BoundModel, value: Boundable, initialTransform: Mat4f, tint: Tint): RenderInstance {
//        check(id !in model.instances) { "Instance with id $id already exists" }
        if (id in model.instances) {
            println("Note: RenderInstance with ID '$id' was not properly closed prior to being recreated, it will be overwritten.")
            model.instances[id]!!.close()
        }
        val instance = RenderInstance(
            id, initialTransform, tint, model, instanceBuffer = surfaceBinding.baseInstanceData,
            jointBuffer = surfaceBinding.jointMatrixData,
            value, onClose = { remove(it) }
        )
        surfaceBinding.rayCasting.add(instance)

        model.instances[id] = instance
        return instance
    }

     fun remove(renderInstance: RenderInstance) {
        renderInstance.model.instances.remove(renderInstance.funId)
        surfaceBinding.rayCasting.remove(renderInstance)

         surfaceBinding.baseInstanceData.free(renderInstance.globalInstancePointer, BaseInstanceData.size)
         if (renderInstance.skin != null) {
             surfaceBinding.jointMatrixData.free(renderInstance.jointMatricesPointer, renderInstance.skin.jointMatrixSize)
         }
         renderInstance.despawned = true
    }


}

private fun checkForFrameDrops(window: WebGPUContext, delta: Duration) {
    val normalFrameTime = (1f / window.refreshRate).seconds
    // It's not exact, but if it's almost twice as long (or more), it means we took too much time to make a frame
    if (delta > normalFrameTime * 1.8f) {
        val missingFrames = (delta / normalFrameTime).roundToInt() - 1
        val plural = missingFrames > 1
        FunContextRegistry.getContext().logger.performance("Frame Drops") {
            "Took $delta to make a frame instead of the usual $normalFrameTime," +
                    " so about $missingFrames ${if (plural) "frames were" else "frame was"} dropped"
        }
    }
}


@Serializable
data class Tint(val color: @Serializable(with = ColorSerializer::class) Color = Color.White, val strength: Float = 0.5f)


/**
 * Stores instance information in one buffer and then a map from each instance index to its instance information in another buffer.
 * @param instanceIndexGetter Gets the index of instance data of the given [RenderInstance].
 */
internal class IndirectInstanceBuffer(
    val ctx: WebGPUContext, val models: Map<ModelId, BoundModel>,
    maxInstances: Int, expectedElementSize: UInt,
    private val instanceIndexGetter: (RenderInstance) -> Int,
) : AutoCloseable {
    override fun close() {
        closeAll(instanceBuffer, instanceIndexBuffer)
    }

    val instanceBuffer =
        ManagedGPUMemory(ctx, initialSizeBytes = (maxInstances * expectedElementSize.toInt()).toULong(), expandable = false, GPUBufferUsage.Storage)

    /**
     * Since instances are not stored contiguously in memory per mesh, we need a separate buffer that IS contiguous per mesh to point each instance
     * to its correct location in the instance buffer.
     */
    val instanceIndexBuffer =
        ManagedGPUMemory(ctx, initialSizeBytes = (maxInstances * Int.SIZE_BYTES).toULong(), expandable = false, GPUBufferUsage.Storage)

    fun newInstance(array: Any): GPUPointer<*> {
        if (arrayByteSize(array) > 0) {
            dirty = true
        }
        return instanceBuffer.new(array)
    }


    fun free(pointer: GPUPointer<*>, elementSize: UInt) {
        instanceBuffer.free(pointer, elementSize)
        if (elementSize > 0u) {
            dirty = true
        }
    }

    /**
     * The index buffer is invalid when new instances are added or removed. This is usually not every frame,
     * so we recreate the index buffer only when that happens (we set this to true, and on render we recreate the buffer and set this to false)
     */
    private var dirty = false

    private fun countInstances() = models.values.sumOf { it.instances.size }


    fun rebuild() {
        if (dirty) {
//            println("Rebuilding instance buffer, we now have ${countInstances()} instances")
            dirty = false
            val indices = IntArray(countInstances())
            var globalI = 0
            for (model in models) {
                // For each model we have a contiguous block of pointers to the instance index
                for (instance in model.value.instances) {
                    indices[globalI++] = instanceIndexGetter(instance.value)
                }
            }
            instanceIndexBuffer.write(indices)
        }
    }
}


fun WorldRendererSurfaceEffect.printInstanceDataDebug() {
    println("=== Instance Data Debug Report ===")
    println("Total models: ${models.size}")

    if (models.isEmpty()) {
        println("No models found - buffers are empty")
        return
    }

    var totalInstances = 0

    models.forEach { (modelId, boundModel) ->
        val instances = boundModel.instances
        totalInstances += instances.size

        println("\n--- Model: $modelId ---")
        println("  Instance count: ${instances.size}")
        println("  Model file: ${boundModel.data.id}")


        instances.values.forEachIndexed { index, instance ->
            println("\n  Instance #${index + 1}:")
            println("    ID: ${instance.funId}")
            println("    Render ID: ${instance.renderId}")

            // Base Instance Data
            println("    Base Instance Data:")
            println("      Transform: ${instance.setTransform}")
            println("      Global Instance Pointer: ${instance.globalInstancePointer.address}")

        }
    }
}

