package io.github.natanfudge.fn.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.lightPos
import io.github.natanfudge.fn.network.ColorSerializer
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.render.utils.*
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.*
import kotlinx.serialization.Serializable


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

class WorldRender(
    val ctx: WebGPUContext,
    val surface: FunSurface,
) : AutoCloseable {
    val uniformBuffer = WorldUniform.createBuffer(ctx, 1u, expandable = false, GPUBufferUsage.Uniform)

    val rayCasting = RayCastingCache<RenderInstance>()
    var selectedObjectId: Int = -1

    var hoveredObject: Boundable? by mutableStateOf(null)

    val vertexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 100_000_000u, expandable = true, GPUBufferUsage.Vertex)
    val indexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 20_000_000u, expandable = true, GPUBufferUsage.Index)

    internal val baseInstanceData: IndirectInstanceBuffer = IndirectInstanceBuffer(
        this, maxInstances = 50_000, expectedElementSize = BaseInstanceData.size
    ) { it.renderId }
    internal val jointMatrixData: IndirectInstanceBuffer = IndirectInstanceBuffer(this, maxInstances = 1_000, expectedElementSize = Mat4f.SIZE_BYTES * 50u) {
        (it.jointMatricesPointer.address / Mat4f.SIZE_BYTES).toInt()
    }

    val models = mutableMapOf<ModelId, BoundModel>()

    val modelBindGroups = mutableMapOf<ModelId, GPUBindGroup>()


    fun createBindGroup(pipeline: GPURenderPipeline) = ctx.device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = listOf(
                BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniformBuffer.buffer)),
                BindGroupEntry(binding = 1u, resource = surface.sampler),
                BindGroupEntry(binding = 2u, resource = BufferBinding(baseInstanceData.instanceBuffer.buffer)),
                BindGroupEntry(binding = 3u, resource = BufferBinding(baseInstanceData.instanceIndexBuffer.buffer)),
                BindGroupEntry(binding = 4u, resource = BufferBinding(jointMatrixData.instanceBuffer.buffer)),
                BindGroupEntry(binding = 5u, resource = BufferBinding(jointMatrixData.instanceIndexBuffer.buffer)),
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
        for (model in models.values) {
            modelBindGroups[model.model.id] = model.createBindGroup(pipeline)
        }
    }


    /**
     * Returns where the use is pointing at in world space
     */
    private fun getCursorRay(camera: Camera, cursorPosition: Offset?, viewProjection: Mat4f, dimensions: FunWindow): Ray {
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

    var cursorPosition: Offset? = null


    fun draw(
        encoder: GPUCommandEncoder,
        worldBindGroup: GPUBindGroup,
        pipeline: GPURenderPipeline,
        dimensions: FunWindow,
        frame: GPUTextureView,
        camera: Camera,
    ) {
        val viewProjection = dimensions.projection * camera.viewMatrix

        // Update selected object based on ray casting
        val rayCast = rayCasting.rayCast(getCursorRay(camera, cursorPosition, viewProjection, dimensions))
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

        baseInstanceData.rebuild()
        jointMatrixData.rebuild()
        pass.setPipeline(pipeline)
        pass.setBindGroup(0u, worldBindGroup)
        pass.setVertexBuffer(0u, vertexBuffer.buffer)
        pass.setIndexBuffer(indexBuffer.buffer, GPUIndexFormat.Uint32)

        var instanceIndex = 0u
        for (model in models.values) {
            for (instance in model.instances) {
                instance.value.updateGPU()
            }

            pass.setBindGroup(1u, modelBindGroups[model.model.id]!!)
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

    fun getOrBindModel(model: Model) = models.computeIfAbsent(model.id) { bind(model) }

    /**
     * Note: [models] is updated by [getOrBindModel]
     */
    private fun bind(model: Model): BoundModel {
        val vertexPointer = vertexBuffer.new(model.mesh.vertices.array)
        val indexPointer = indexBuffer.new(model.mesh.indices.array)
        val bound = BoundModel(
            model, ctx,
            // These values are indices, not bytes, so we need to divide by the size of the value
            firstIndex = (indexPointer.address / Int.SIZE_BYTES.toUInt()).toUInt(),
            baseVertex = (vertexPointer.address / VertexArrayBuffer.StrideBytes).toInt(),
            world = this
        )
        if (pipeline != null) {
            modelBindGroups[model.id] = bound.createBindGroup(pipeline!!)
        } // If pipeline is null, the modelBindGroups will be built once it is not null

        return bound
    }


    /**
     * Note: `model.instances` is updated by [VisibleBoundModel.spawn]
     */
    fun spawn(id: FunId, model: BoundModel, value: Boundable, initialTransform: Mat4f, tint: Tint): RenderInstance {
        val instance = RenderInstance(id, initialTransform, tint, model, this, value)
        rayCasting.add(instance)

        return instance
    }

    internal fun remove(renderInstance: RenderInstance) {
        renderInstance.bound.instances.remove(renderInstance.funId)
        rayCasting.remove(renderInstance)
    }

    override fun close() {
        closeAll(vertexBuffer, indexBuffer,  uniformBuffer, baseInstanceData, jointMatrixData)
        modelBindGroups.values.forEach { it.close() }
        models.forEach { it.value.close() }
    }
}

@Serializable
data class Tint(val color: @Serializable(with = ColorSerializer::class) Color, val strength: Float = 0.5f)


/**
 * Stores instance information in one buffer and then a map from each instance index to its instance information in another buffer.
 * @param instanceIndexGetter Gets the index of instance data of the given [RenderInstance].
 */
internal class IndirectInstanceBuffer(private val world: WorldRender, maxInstances: Int, expectedElementSize: UInt,
                                      private val instanceIndexGetter: (RenderInstance) -> Int): AutoCloseable {
    override fun close() {
        closeAll(instanceBuffer, instanceIndexBuffer)
    }
    val instanceBuffer =
        ManagedGPUMemory(world.ctx, initialSizeBytes = (maxInstances * expectedElementSize.toInt()).toULong(), expandable = false, GPUBufferUsage.Storage)

    /**
     * Since instances are not stored contiguously in memory per mesh, we need a separate buffer that IS contiguous per mesh to point each instance
     * to its correct location in the instance buffer.
     */
    val instanceIndexBuffer =
        ManagedGPUMemory(world.ctx, initialSizeBytes = (maxInstances * Int.SIZE_BYTES).toULong(), expandable = false, GPUBufferUsage.Storage)

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

    private fun countInstances() = world.models.values.sumOf { it.instances.size }


    fun rebuild() {
        if (dirty) {
            dirty = false
            val indices = IntArray(countInstances())
            var globalI = 0
            for (model in world.models) {
                // For each model we have a contiguous block of pointers to the instance index
                for (instance in model.value.instances) {
                    indices[globalI++] = instanceIndexGetter(instance.value)
                }
            }
            instanceIndexBuffer.write(indices)
        }
    }
}

