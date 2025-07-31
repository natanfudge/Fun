package io.github.natanfudge.fn.core.newstuff

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.render.utils.GPUPointer
import io.github.natanfudge.fn.render.utils.ManagedGPUMemory
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.ygdrasil.webgpu.*
import korlibs.time.seconds
import korlibs.time.times
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.time.Duration

fun IntSize.toExtent3D(depthOrArrayLayers: Int = 1) = Extent3D(width.toUInt(), height = height.toUInt(), depthOrArrayLayers.toUInt())


class WorldRendererWindowSizeEffect(size: IntSize, ctx: NewWebGPUContext) : InvalidationKey() {
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
class WorldRendererSurfaceEffect(val ctx: NewWebGPUContext) : InvalidationKey() {

    init {
        println("Foo")
        println("Init WorldRendererSurfaceEffect")
    }

    val uniformBuffer = WorldUniform.createBuffer(ctx, 1u, expandable = false, GPUBufferUsage.Uniform)

    init {
        println("After uniformBuffer")
    }

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


class NewWorldRenderer(val surfaceHolder: NewWebGPUSurfaceHolder) : NewFun("WorldRenderer") {
    val surfaceBinding by cached(surfaceHolder.surface) {
        // Recreated on every surface change
        WorldRendererSurfaceEffect(surfaceHolder.surface)
    }



    val index = rendererNextIndex++

    var sizeBinding by cached(surfaceBinding) {
        WorldRendererWindowSizeEffect(surfaceHolder.size, surfaceHolder.surface)
    }

    init {
        // I prefer manually reconstructing the WorldRendererWindowSizeEffect on window size change instead of
        // having the size as an invalidation key and refreshing the app, because this way the app doesn't get refreshed
        // constantly when the window is resized. I try to keep refreshes to be a dev-only reload thing, and not be a thing
        // the user experiences all the time like when he resizes the window.
        events.windowResized.listen {
            if (!it.isEmpty) {
                sizeBinding = WorldRendererWindowSizeEffect(it, surfaceHolder.surface)
            }
        }
    }

    val pipelineHolder = NewReloadingPipeline(
        "Object",
        surfaceHolder,
        vertexSource = ShaderSource.HotFile("object"),
    ) { vertex, fragment ->
        RenderPipelineDescriptor(
            label = "Fun Object Pipeline #${pipelines++}",
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

    var camera = NewDefaultCamera()

    init {
        val x = 2
    }

    var bindgroup: GPUBindGroup by cached(surfaceBinding) {
        surfaceBinding.createBindGroup(pipelineHolder.pipeline)
    }

    override fun cleanup() {
        val x = 2
    }


    init {
//        pipelineHolder.pipelineClosed.listen {
//            println("Closing world bindgroup on #${index}")
//            bindgroup?.close()
//            bindgroup = null
//            println("Closing model bindgroups on #${index}")
//            surfaceBinding.models.forEach { it.value.closeBindGroup() }
//        }
        pipelineHolder.pipelineLoaded.listen { pipeline ->
            println("Creating bindgroup on new pipeline on #${index}")
            bindgroup = surfaceBinding.createBindGroup(pipeline)
            println("Recreating bindgroups for all models on new pipeline on #${index}")
            surfaceBinding.models.forEach { it.value.recreateBindGroup(pipeline) }
        }

        events.windowResized.listen {
            projection = calculateProjectionMatrix(it)
        }

        events.frame.listen { delta ->
            val ctx = surfaceHolder.surface
            if (ctx.window.size.isEmpty) return@listen
            checkForFrameDrops(ctx, delta)
            val encoder = ctx.device.createCommandEncoder()

            // This call (context.getCurrentTexture()) invokes VSync (so it stalls here usually)
            // It's important to call this here and not nearby any user code, as the thread will spend a lot of time here,
            // and so if user code both calls this method and changes something, they are at great risk of a crash on DCEVM reload, see
            // https://github.com/JetBrains/JetBrainsRuntime/issues/534
            val underlyingWindowFrame = ctx.surface.getCurrentTexture()
            val windowTexture =
                underlyingWindowFrame.texture.createView(descriptor = TextureViewDescriptor(label = "Texture created directly from device ${ctx.index}"))

            val viewProjection = projection * camera.viewMatrix

            val dimensions = surfaceHolder.size

            // Update selected object based on ray casting
            val rayCast = surfaceBinding.rayCasting.rayCast(getCursorRay(camera, cursorPosition, viewProjection, dimensions))
            hoveredObject = rayCast?.value
            selectedObjectId = rayCast?.renderId ?: -1

            val uniform = WorldUniform(
                viewProjection, camera.position, lightPos,
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
                        model.model.mesh.indexCount,
                        instanceCount = instances,
                        firstIndex = model.firstIndex,
                        baseVertex = model.baseVertex,
                        firstInstance = instanceIndex
                    )
                    instanceIndex += instances
                } else {
                    println("Skipping model because its bindgroup is null, not sure if this will work correctly")
                }

            }
            pass.end()

            val err = ctx.error
            if (err != null) {
                ctx.error = null
                throw err
            }

            ctx.device.queue.submit(listOf(encoder.finish()));

            ctx.surface.present()

            windowTexture.close()
            underlyingWindowFrame.texture.close()
            encoder.close()
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
    private fun getCursorRay(camera: Camera, cursorPosition: Offset?, viewProjection: Mat4f, dimensions: IntSize): Ray {
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

    internal fun remove(renderInstance: RenderInstance) {
        renderInstance.bound.instances.remove(renderInstance.funId)
        surfaceBinding.rayCasting.remove(renderInstance)
    }


}

private fun checkForFrameDrops(window: NewWebGPUContext, delta: Duration) {
    val normalFrameTime = (1f / window.refreshRate).seconds
    // It's not exact, but if it's almost twice as long (or more), it means we took too much time to make a frame
    if (delta > normalFrameTime * 1.8f) {
        val missingFrames = (delta / normalFrameTime).roundToInt() - 1
        val plural = missingFrames > 1
        NewFunContextRegistry.getContext().logger.performance("Frame Drops") {
            "Took $delta to make a frame instead of the usual $normalFrameTime," +
                    " so about $missingFrames ${if (plural) "frames were" else "frame was"} dropped"
        }
    }
}
