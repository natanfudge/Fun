package io.github.natanfudge.fn.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.core.FunWorldRender
import io.github.natanfudge.fn.files.Image
import io.github.natanfudge.fn.lightPos
import io.github.natanfudge.fn.network.ColorSerializer
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.physics.Renderable
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.*
import kotlinx.serialization.Serializable


interface BoundModel {
    fun getOrSpawn(id: FunId, value: Renderable, tint: Tint): RenderInstance
}

/**
 * Stores GPU information about all instances of a [Model].
 */
class VisibleBoundModel(
    val model: Model, ctx: WebGPUContext, val firstIndex: UInt, val baseVertex: Int,
    val world: WorldRender,
) : AutoCloseable, BoundModel {
    val image = model.material.texture

    val texture = ctx.device.createTexture(
        TextureDescriptor(
            size = if (image != null) Extent3D(
                image.width.toUInt(),
                image.height.toUInt(),
                1u
            ) else Extent3D(1u, 1u),
            // We loaded srgb data from the png so we specify srgb here. If your data is in a linear color space you can do RGBA8Unorm instead
            format = GPUTextureFormat.RGBA8UnormSrgb,
            usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst),
            label = "Model Texture"
        )
    )

    init {
        if (image != null) {
            ctx.device.copyExternalImageToTexture(
                source = image.bytes,
                texture = texture,
                width = image.width, height = image.height
            )
        }
    }

    val textureView = texture.createView()

    val instances = mutableMapOf<FunId, VisibleRenderInstance>()

//    val instanceIds = mutableListOf<Int>()

//    private fun spawn(value: Physical, color: Color = Color.White): RenderInstance =


    override fun getOrSpawn(id: FunId, value: Renderable, tint: Tint): VisibleRenderInstance {
        val instance = instances.computeIfAbsent(id) {
            world.spawn(id, this, value, tint)
        }
        instance.value = value
        return instance
    }


    override fun close() {
        //
//        if(model.instanceIds.isEmpty()) {
//            world.vertexBuffer.free(
//                GPUPointer(model.baseVertex.toULong() * VertexArrayBuffer.StrideBytes),
//                model.model.mesh.verticesByteSize.toUInt()
//            )
//            world.indexBuffer.free(
//                model.firstIndex * Int.SIZE_BYTES.toUInt()
//            )
//        }
        closeAll(texture, textureView)
    }
}


///**
// * Seperated from [WorldRender] in order to be able to maintain the same [WorldRender] between shader reloads, as the [io.github.natanfudge.fn.render.WorldBindGroups]
// * will be swapped out when that happens.
// */
//class WorldBindGroups(
//    val pipeline: GPURenderPipeline,
//    val world: GPUBindGroup,
//    val models: List<GPUBindGroup>,
//) : AutoCloseable {
//    override fun close() {
//        world.close()
//        models.forEach { it.close() }
//    }
//}

object GPUInstance : Struct5<Mat4f, Mat3f, Color, Float, Int, GPUInstance>(Mat4fDT, Mat3fDT, ColorDT, FloatDT, IntDT)

object WorldUniform : Struct6<Mat4f, Vec3f, Vec3f, UInt, UInt, UInt, WorldUniform>(
    Mat4fDT, Vec3fDT, Vec3fDT, UIntDT, UIntDT, UIntDT
)

class WorldRender(
    val ctx: WebGPUContext,
    val surface: FunSurface,
) : FunWorldRender, AutoCloseable {

//    var camera : Camera? = null

    var worldInstances = 0
    val uniformBuffer = WorldUniform.createBuffer(ctx, 1u, expandable = false, GPUBufferUsage.Uniform)

    val rayCasting = RayCastingCache<VisibleRenderInstance>()
    var selectedObjectId: Int = -1

    override var hoveredObject: Boundable? by mutableStateOf(null)

    val vertexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 1_000_000u, expandable = true, GPUBufferUsage.Vertex)
    val indexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 200_000u, expandable = true, GPUBufferUsage.Index)

    val instanceBuffer = GPUInstance.createBuffer(ctx, 10u, expandable = false, GPUBufferUsage.Storage)

    /**
     * Since instances are not stored contiguously in memory per mesh, we need a separate buffer that IS contiguous per mesh to point each instance
     * to its correct location in the instance buffer.
     */
    val instanceIndexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = Int.SIZE_BYTES.toULong() * 10u, expandable = false, GPUBufferUsage.Storage)

    //    val models = mutableListOf<BoundModel>()
    val models = mutableMapOf<ModelId, VisibleBoundModel>()

    //    var bindGroup =
    val modelBindGroups = mutableListOf<GPUBindGroup>()


    fun createBindGroup(pipeline: GPURenderPipeline) = ctx.device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = listOf(
                BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniformBuffer.buffer)),
                BindGroupEntry(binding = 1u, resource = surface.sampler),
                BindGroupEntry(binding = 2u, resource = BufferBinding(instanceBuffer.buffer)),
                BindGroupEntry(binding = 3u, resource = BufferBinding(instanceIndexBuffer.buffer)),
            )
        )
    )

    private var pipeline: GPURenderPipeline? = null

    /**
     * Called whenever the pipeline changes to update the model bind groups
     */
    fun onPipelineChanged(pipeline: GPURenderPipeline) {
        this.pipeline = pipeline
        modelBindGroups.clear()
        modelBindGroups.addAll(
            models.map {
                createModelBindGroup(pipeline, it.value)
            }
        )
    }

    private fun createModelBindGroup(pipeline: GPURenderPipeline, model: VisibleBoundModel) = ctx.device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(1u),
            entries = listOf(
                BindGroupEntry(
                    binding = 0u,
                    resource = model.textureView
                ),
            )
        )
    )


    /**
     * Returns where the use is pointing at in world space
     */
    private fun getCursorRay(camera: Camera, cursorPosition: Offset?, viewProjection: Mat4f, dimensions: FunFixedSizeWindow): Ray {
        if (cursorPosition != null) {
            val ray = Selection.orbitalSelectionRay(
                cursorPosition,
                IntSize(dimensions.dims.width, dimensions.dims.height),
                viewProjection
            )
            return ray
        } else {
            return Ray(camera.position, camera.forward)
        }
    }

    override var cursorPosition: Offset? = null


    fun draw(
        encoder: GPUCommandEncoder,
        worldBindGroup: GPUBindGroup,
        pipeline: GPURenderPipeline,
        dimensions: FunFixedSizeWindow,
        frame: GPUTextureView,
        camera: Camera,
    ) {
        // If the bindgroups are not ready yet, don't do anything
//        if (models.size != modelBindGroups.size) return
        val viewProjection = dimensions.projection * camera.viewMatrix

        // Update selected object based on ray casting
        val rayCast = rayCasting.rayCast(getCursorRay(camera, cursorPosition, viewProjection, dimensions))
        hoveredObject = rayCast?.value
        selectedObjectId = rayCast?.renderId ?: -1

        val selectedObjectId = rayCast?.renderId?.toUInt() ?: 9999u
        //TODO: stop passing the selected object
        val uniform = WorldUniform(
            viewProjection, camera.position, lightPos,
            dimensions.dims.width.toUInt(), dimensions.dims.height.toUInt(),
            selectedObjectId
        )


        uniformBuffer[GPUPointer(0u)] = uniform


        val renderPassDescriptor = RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = dimensions.msaaTextureView,
                    resolveTarget = frame,
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

        val pass = encoder.beginRenderPass(renderPassDescriptor)

        // SLOW: should only run this when the buffer is invalidated
        rebuildInstanceIndexBuffer()
        pass.setPipeline(pipeline)
        pass.setBindGroup(0u, worldBindGroup)
        pass.setVertexBuffer(0u, vertexBuffer.buffer)
        pass.setIndexBuffer(indexBuffer.buffer, GPUIndexFormat.Uint32)

        var instanceIndex = 0u
        for ((i, model) in models.values.withIndex()) {
            pass.setBindGroup(1u, modelBindGroups[i])
            val instances = model.instances.size.toUInt()
            pass.drawIndexed(
                model.model.mesh.indexCount,
                instanceCount = instances,
                firstIndex = model.firstIndex,
                baseVertex = model.baseVertex,
                firstInstance = instanceIndex
            )
            instanceIndex += instances
        }
        pass.end()
    }

    override fun getOrBindModel(model: Model) = models.computeIfAbsent(model.id) { bind(model) }

    /**
     * Note: [models] is updated by [getOrBindModel]
     */
    private fun bind(model: Model): VisibleBoundModel {
        val vertexPointer = vertexBuffer.new(model.mesh.vertices.array)
        val indexPointer = indexBuffer.new(model.mesh.indices.array)
        val bound = VisibleBoundModel(
            model, ctx,
            // These values are indices, not bytes, so we need to divide by the size of the value
            firstIndex = (indexPointer.address / Int.SIZE_BYTES.toUInt()).toUInt(),
            baseVertex = (vertexPointer.address / VertexArrayBuffer.StrideBytes).toInt(),
            world = this
        )
        if (pipeline != null) {
            // If pipeline is null, the modelBindGroups will be built once it is not null
            modelBindGroups.add(createModelBindGroup(pipeline!!, bound))
        }
//        models[model.id] = bound
        return bound
    }

    /**
     * Note: `model.instances` is updated by [VisibleBoundModel.getOrSpawn]
     */
    fun spawn(id: FunId, model: VisibleBoundModel, value: Renderable, tint: Tint): VisibleRenderInstance {
        val globalId = worldInstances
        // Match the global index with the instance index
//        model.instanceIds.add(globalId)
        worldInstances++

        // SLOW: should reconsider passing normal matrices always
        val normalMatrix = Mat3f.normalMatrix(value.transform)
        val pointer = GPUInstance.new(
            instanceBuffer, value.transform, normalMatrix, tint.color, tint.strength, if (model.image == null) 0 else 1
        )

        val instance = VisibleRenderInstance(pointer, globalId, id, model, this, value)
//        model.instances[id] = instance
        rayCasting.add(instance)

        return instance
    }

    private fun rebuildInstanceIndexBuffer() {
        val indices = IntArray(worldInstances)
        var globalI = 0
        for (model in models) {
            // For each model we have a contiguous block of pointers to the instance index
            for (instance in model.value.instances) {
                indices[globalI++] = instance.value.renderId
            }
        }
        instanceIndexBuffer.write(indices)
    }

    override fun close() {
        closeAll(vertexBuffer, indexBuffer, instanceBuffer, instanceIndexBuffer, uniformBuffer)
        modelBindGroups.forEach { it.close() }
        models.forEach { it.value.close() }
    }
}
@Serializable
data class Tint(val color: @Serializable(with = ColorSerializer::class) Color, val strength: Float = 0.5f)


interface RenderInstance {

    fun setTransform(transform: Mat4f)

    fun setTintColor(color: Color)

    fun setTintStrength(strength: Float)

    fun despawn()
}

class VisibleRenderInstance(
    @PublishedApi internal val pointer: GPUPointer<GPUInstance>,
    internal val renderId: Int,
    val funId: FunId,
//    val value: T,
    private val model: VisibleBoundModel,
    @PublishedApi internal val world: WorldRender,
    var value: Boundable,
) : Boundable, RenderInstance {
    override val boundingBox: AxisAlignedBoundingBox
        get() = value.boundingBox
    override val data: Any?
        get() = value.data

    var despawned = false

    /**
     * Removes this instance from the world, cleaning up any held resources.
     * After despawning, attempting to transform this instance will fail.
     */
    override fun despawn() {
        model.instances.remove(funId)
        world.instanceBuffer.free(pointer, GPUInstance.size)
        world.rayCasting.remove(this)
        despawned = true
    }

    private fun checkDespawned() {
        if (despawned) throw IllegalStateException("Attempt to transform despawned object")
    }

    override fun setTransform(transform: Mat4f) {
        checkDespawned()
        GPUInstance.setFirst(world.instanceBuffer, pointer, transform)
    }

    override fun setTintColor(color: Color) {
        checkDespawned()
        GPUInstance.setThird(world.instanceBuffer, pointer, color)
    }

    override fun setTintStrength(strength: Float) {
        checkDespawned()
        GPUInstance.setFourth(world.instanceBuffer, pointer, strength)
    }
}


data class Model(val mesh: Mesh, val id: ModelId, val material: Material = Material())

typealias ModelId = String

class Material(val texture: Image? = null)


