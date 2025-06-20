package io.github.natanfudge.fn.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.core.FunWorldRender
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.lightPos
import io.github.natanfudge.fn.network.ColorSerializer
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.physics.Renderable
import io.github.natanfudge.fn.util.ConsecutiveIndexProvider
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.*
import kotlinx.serialization.Serializable


interface BoundModel {
    fun spawn(id: FunId, value: Renderable, tint: Tint): RenderInstance
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


    override fun spawn(id: FunId, value: Renderable, tint: Tint): VisibleRenderInstance {
        check(id !in instances) { "Instance with id $id already exists" }
        val instance = world.spawn(id, this, value, tint)
        instances[id] = instance
//        val instance = instances.computeIfAbsent(id) {
        return instance
//        }
//        instance.value = value
//        return instance
    }


    override fun close() {
        closeAll(texture, textureView)
    }
}


object GPUInstance : Struct5<Mat4f, Mat3f, Color, Float, Int, GPUInstance>(Mat4fDT, Mat3fDT, ColorDT, FloatDT, IntDT)

object WorldUniform : Struct5<Mat4f, Vec3f, Vec3f, UInt, UInt, WorldUniform>(
    Mat4fDT, Vec3fDT, Vec3fDT, UIntDT, UIntDT
)

class WorldRender(
    val ctx: WebGPUContext,
    val surface: FunSurface,
) : FunWorldRender, AutoCloseable {

//    private val instanceIndexProvider = ConsecutiveIndexProvider()

    val uniformBuffer = WorldUniform.createBuffer(ctx, 1u, expandable = false, GPUBufferUsage.Uniform)

    val rayCasting = RayCastingCache<VisibleRenderInstance>()
    var selectedObjectId: Int = -1

    override var hoveredObject: Boundable? by mutableStateOf(null)

    val vertexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 100_000_000u, expandable = true, GPUBufferUsage.Vertex)
    val indexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 20_000_000u, expandable = true, GPUBufferUsage.Index)

    // SUS: I made this humongously big because I didn't implement freeing yet, so when we restart the app the instance buffer keeps filling up.
    // We should strive to make this smaller afterwards, to identify memory leaks (obv now we have a mega memory leak)
    private val maxIndices = 50000u

    val instanceBuffer = GPUInstance.createBuffer(ctx, maxIndices, expandable = false, GPUBufferUsage.Storage)

    /**
     * Since instances are not stored contiguously in memory per mesh, we need a separate buffer that IS contiguous per mesh to point each instance
     * to its correct location in the instance buffer.
     */
    val instanceIndexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = Int.SIZE_BYTES.toULong() * maxIndices, expandable = false, GPUBufferUsage.Storage)

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
        val viewProjection = dimensions.projection * camera.viewMatrix

        // Update selected object based on ray casting
        val rayCast = rayCasting.rayCast(getCursorRay(camera, cursorPosition, viewProjection, dimensions))
        //TODO: raycasting stops working after restart... //TODO: I think it's because i'm not freeing the instance buffer, so everything just stays
//        println("raycast: $rayCast")
        hoveredObject = rayCast?.value
        selectedObjectId = rayCast?.renderId ?: -1

        val uniform = WorldUniform(
            viewProjection, camera.position, lightPos,
            dimensions.dims.width.toUInt(), dimensions.dims.height.toUInt()
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
        return bound
    }

    private val instanceBufferElementCount get() = GPUInstance.fullElements(instanceBuffer).toInt()

    /**
     * Note: `model.instances` is updated by [VisibleBoundModel.spawn]
     */
    fun spawn(id: FunId, model: VisibleBoundModel, value: Renderable, tint: Tint): VisibleRenderInstance {
//        val globalId = instanceIndexProvider.get()


        // SLOW: should reconsider passing normal matrices always
        val normalMatrix = Mat3f.normalMatrix(value.transform)
        val pointer = GPUInstance.new(
            instanceBuffer, value.transform, normalMatrix, tint.color, tint.strength, if (model.image == null) 0 else 1
        )

        // The index is a pointer to the instance in the instanceBuffer array (wgpu indexes a struct of arrays, so it needs an index, not a pointer)
        val instanceIndex = (pointer.address / GPUInstance.size).toInt()

        val instance = VisibleRenderInstance(pointer, instanceIndex, id, model, this, value)
        rayCasting.add(instance)

        return instance
    }

    private fun rebuildInstanceIndexBuffer() {
        val indices = IntArray(instanceBufferElementCount)
        var globalI = 0
        for (model in models) {
            // For each model we have a contiguous block of pointers to the instance index
            for (instance in model.value.instances) {
                indices[globalI++] = instance.value.renderId
            }
        }
        instanceIndexBuffer.write(indices)
    }

    internal fun remove(renderInstance: VisibleRenderInstance) {
        renderInstance.model.instances.remove(renderInstance.funId)
        instanceBuffer.free(renderInstance.pointer, GPUInstance.size)
        rayCasting.remove(renderInstance)
//        instanceIndexProvider.free(renderInstance.renderId)
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
    val model: VisibleBoundModel,
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
        world.remove(this)
        despawned = true
    }

    private fun checkDespawned() {
        if (despawned) throw IllegalStateException("Attempt to transform despawned object")
    }

    override fun setTransform(transform: Mat4f) {
        checkDespawned()
        GPUInstance.setFirst(world.instanceBuffer, pointer, transform)
        // Update normal matrix, as the transform changed
        GPUInstance.setSecond(world.instanceBuffer, pointer, Mat3f.normalMatrix(transform))
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

data class Transform(
    val position: Vec3f = Vec3f.zero(),
    val rotation: Quatf = Quatf.identity(),
    var scale: Vec3f = Vec3f(1f, 1f, 1f),
)


data class Model(val mesh: Mesh, val id: ModelId, val material: Material = Material(), val initialTransform: Transform = Transform()) {
    companion object;
}

typealias ModelId = String

class Material(val texture: FunImage? = null)


