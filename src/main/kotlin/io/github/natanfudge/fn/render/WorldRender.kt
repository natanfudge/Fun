package io.github.natanfudge.fn.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.files.Image
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.*


//TODO: 1. Screen-space picking
// 2.
/**
 * Stores GPU information about all instances of a [Model].
 */
class BoundModel(
    val model: Model, ctx: WebGPUContext, val firstIndex: UInt, val baseVertex: Int,
    val world: WorldRender,
) : AutoCloseable {
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
            usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
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

    val instanceIds = mutableListOf<Int>()

    fun spawn(transform: Mat4f = Mat4f.identity(), color: Color = Color.White) = world.spawn(this, transform, color)


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


/**
 * Seperated from [WorldRender] in order to be able to maintain the same [WorldRender] between shader reloads, as the [io.github.natanfudge.fn.render.WorldBindGroups]
 * will be swapped out when that happens.
 */
class WorldBindGroups(
    val pipeline: GPURenderPipeline,
    val world: GPUBindGroup,
    val models: List<GPUBindGroup>,
) : AutoCloseable {
    override fun close() {
        world.close()
        models.forEach { it.close() }
    }
}

object GPUInstance : Struct4<Mat4f, Mat3f, Color, Int, GPUInstance>(Mat4fDT, Mat3fDT, ColorDT, IntDT)

object WorldUniform : Struct6<Mat4f, Vec3f, Vec3f, UInt, UInt, UInt, WorldUniform>(
    Mat4fDT, Vec3fDT, Vec3fDT, UIntDT, UIntDT, UIntDT
)

class WorldRender(
    val ctx: WebGPUContext,
    val surface: FunSurface,
) : AutoCloseable {

//    var camera : Camera? = null

    var worldInstances = 0
    val uniformBuffer = WorldUniform.createBuffer(ctx, 1u, expandable = false, GPUBufferUsage.Uniform)

    val rayCasting = RayCastingCache<RenderInstance>()
    var selectedObjectId: Int = -1

    val vertexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 1_000_000u, expandable = true, GPUBufferUsage.Vertex)
    val indexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 200_000u, expandable = true, GPUBufferUsage.Index)

    val instanceBuffer = GPUInstance.createBuffer(ctx, 10u, expandable = false, GPUBufferUsage.Storage)

    /**
     * Since instances are not stored contiguously in memory per mesh, we need a separate buffer that IS contiguous per mesh to point each instance
     * to its correct location in the instance buffer.
     */
    val instanceIndexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = Int.SIZE_BYTES.toULong() * 10u, expandable = false, GPUBufferUsage.Storage)
    val models = mutableListOf<BoundModel>()
    val modelBindGroups = mutableListOf<GPUBindGroup>()

    fun createBindGroups(pipeline: GPURenderPipeline): WorldBindGroups = WorldBindGroups(
        world = ctx.device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniformBuffer.buffer)),
                    BindGroupEntry(binding = 1u, resource = surface.sampler),
                    BindGroupEntry(binding = 2u, resource = BufferBinding(instanceBuffer.buffer)),
                    BindGroupEntry(binding = 3u, resource = BufferBinding(instanceIndexBuffer.buffer)),
                )
            )
        ),
        models = models.map {
            ctx.device.createBindGroup(
                BindGroupDescriptor(
                    layout = pipeline.getBindGroupLayout(1u),
                    entries = listOf(
                        BindGroupEntry(
                            binding = 0u,
                            resource = it.textureView
                        ),
                    )
                )
            )
        },
        pipeline = pipeline
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


    fun draw(
        encoder: GPUCommandEncoder,
        bindGroups: WorldBindGroups,
        dimensions: FunFixedSizeWindow,
        frame: GPUTextureView,
        camera: Camera,
        cursorPosition: Offset?,
    ) {
        val viewProjection = dimensions.projection * camera.viewMatrix

        // Update selected object based on ray casting
        val rayCast = rayCasting.rayCast(getCursorRay(camera, cursorPosition, viewProjection, dimensions))
        selectedObjectId = rayCast?.globalId ?: -1

        val selectedObjectId = rayCast?.globalId?.toUInt() ?: 9999u
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
        pass.setPipeline(bindGroups.pipeline)
        pass.setBindGroup(0u, bindGroups.world)
        pass.setVertexBuffer(0u, vertexBuffer.buffer)
        pass.setIndexBuffer(indexBuffer.buffer, GPUIndexFormat.Uint32)

        var instanceIndex = 0u
        for ((i, model) in models.withIndex()) {
            pass.setBindGroup(1u, bindGroups.models[i])
            val instances = model.instanceIds.size.toUInt()
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

    fun bind(model: Model): BoundModel {
        val vertexPointer = vertexBuffer.new(model.mesh.vertices.array)
        val indexPointer = indexBuffer.new(model.mesh.indices.array)
        val bound = BoundModel(
            model, ctx,
            // These values are indices, not bytes, so we need to divide by the size of the value
            firstIndex = (indexPointer.address / Int.SIZE_BYTES.toUInt()).toUInt(),
            baseVertex = (vertexPointer.address / VertexArrayBuffer.StrideBytes).toInt(),
            world = this
        )
        models.add(bound)
        return bound
    }

    fun spawn(model: BoundModel, transform: Mat4f = Mat4f.identity(), color: Color = Color.White): RenderInstance {
        val globalId = worldInstances
        // Match the global index with the instance index
        model.instanceIds.add(globalId)
        worldInstances++

        // SLOW: should reconsider passing normal matrices always
        val normalMatrix = Mat3f.normalMatrix(transform)
        val pointer = GPUInstance.new(instanceBuffer, transform, normalMatrix, color, if (model.image == null) 0 else 1)

        val instance = RenderInstance(pointer, globalId, model, this, transform)
        rayCasting.add(instance)

        return instance
    }

    fun rebuildInstanceIndexBuffer() {
        val indices = IntArray(worldInstances)
        var globalI = 0
        for (model in models) {
            // For each model we have a contiguous block of pointers to the instance index
            for (instance in model.instanceIds) {
                indices[globalI++] = instance
            }
        }
        instanceIndexBuffer.write(indices)
    }

    override fun close() {
        closeAll(vertexBuffer, indexBuffer, instanceBuffer, instanceIndexBuffer, uniformBuffer)
        modelBindGroups.forEach { it.close() }
        models.forEach { it.close() }
    }
}


class RenderInstance(
    @PublishedApi internal val pointer: GPUPointer<GPUInstance>,
    internal val globalId: Int,
    private val model: BoundModel,
    @PublishedApi internal val world: WorldRender,
    transform: Mat4f,
) : Boundable {
    private var baseAABB = getAxisAlignedBoundingBox(model.model.mesh)
    override var boundingBox: AABoundingBox = baseAABB.transformed(transform)

    val transform = transform.copy()

    var despawned = false

    /**
     * Removes this instance from the world, cleaning up any held resources.
     * After despawning, attempting to transform this instance will fail.
     */
    fun despawn() {
        model.instanceIds.remove(globalId)
        world.instanceBuffer.free(pointer, GPUInstance.size)
        world.rayCasting.remove(this)
        despawned = true
    }

    fun setTransform(transform: Mat4f) {
        this.transform.set(transform)
        _updateTransform()
    }

    /**
     * Allows mutating the transform in-place, after which the new transform will be used.
     */
    inline fun setTransform(transform: (Mat4f) -> Unit) {
        transform(this.transform)
        _updateTransform()
    }

    @PublishedApi
    internal fun _updateTransform() {
        check(!despawned) { "Attempt to transform despawned object" }
        GPUInstance.setFirst(world.instanceBuffer, pointer, this.transform)
        boundingBox = baseAABB.transformed(transform)


        // LATER: might need to do something like this to update ray casting cache, for now the current approach works because there's no real BVH or smthn
//        world.rayCasting.remove(this)
//        world.rayCasting.add(this)
    }

    /**
     * Premultiplies the current transformation with [transform], effectively applying [transform] AFTER the current transformation.
     */
    fun transform(transform: Mat4f) {
        transform.mul(this.transform, this.transform)
        _updateTransform()
    }
}


data class Model(val mesh: Mesh, val material: Material = Material()/*, val name: String*/)
class Material(val texture: Image? = null)


