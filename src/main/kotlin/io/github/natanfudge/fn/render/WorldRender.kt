package io.github.natanfudge.fn.render

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.files.Image
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.util.concatArrays
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec4f
import io.ygdrasil.webgpu.*

// + 4 is for textured: bool
private val instanceBytes = (Mat4f.SIZE_BYTES + Mat3f.SIZE_BYTES + Vec4f.SIZE_BYTES + 4u).wgpuAlign()

//class BoundModel2(val model: Model, val pipelineLifecycle: Lifecycle<*, ReloadingPipeline>, val surfaceLifecycle: Lifecycle<*, FunSurface>) {
//
//}

/**
 * Stores GPU information about all instances of a [Model].
 */
class BoundModel(val model: Model, ctx: WebGPUContext, val pipeline: GPURenderPipeline, val firstIndex: UInt, val baseVertex: Int) : AutoCloseable {
    val device = ctx.device

    // SLOW: with i need to combine the buffers, esp with multiDrawIndirect
//    val vertexBuffer = device.createBuffer(
//        BufferDescriptor(
//            size = model.mesh.verticesByteSize,
//            usage = setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
//        )
//    )
//
//    val indexBuffer = device.createBuffer(
//        BufferDescriptor(
//            size = model.mesh.indexCount * Float.SIZE_BYTES.toULong(),
//            usage = setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
//        )
//    )
    val instanceBuffer = ManagedGPUMemory(ctx, initialSizeBytes = (instanceBytes * 10u), GPUBufferUsage.Storage)

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

    //TODO: maybe I want to create the bindgroup in a seperate lifecycle to make it so i don't need to recreate all the buffers when reloading shaders.

    val bindGroup = device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(1u),
            entries = listOf(
                BindGroupEntry(
                    binding = 0u,
                    resource = BufferBinding(buffer = instanceBuffer.buffer)
                ),
                BindGroupEntry(
                    binding = 1u,
                    resource = textureView
                ),
            )
        )
    )

    var instances = 0u

//    init {
//        device.queue.writeBuffer(vertexBuffer, 0u, model.mesh.vertices.array)
//        device.queue.writeBuffer(indexBuffer, 0u, model.mesh.indices.array)
//    }

//    fun draw(pass: GPURenderPassEncoder) {
//        pass.setBindGroup(1u, bindGroup)
//        pass.setVertexBuffer(0u, vertexBuffer)
//        pass.setIndexBuffer(indexBuffer, GPUIndexFormat.Uint32)
//        pass.drawIndexed(model.mesh.indexCount, instanceCount = instances)
//    }

    fun spawn(transform: Mat4f = Mat4f.identity(), color: Color = Color.White): RenderInstance {
        instances++

        val instance = instanceBuffer.alloc(instanceBytes)

        val normalMatrix = Mat3f.normalMatrix(transform).array
        val instanceData = concatArrays(
            transform.array, normalMatrix, color.toFloatArray(),
            floatArrayOf(if (image == null) 0f else 1f) // "textured" boolean
        )
        instanceBuffer.write(instance, instanceData)

        return RenderInstance(instance, instanceBuffer)
    }

    override fun close() {
        closeAll(instanceBuffer, texture, textureView, bindGroup)
    }
}

private fun Color.toFloatArray() = floatArrayOf(red, green, blue, alpha)

@JvmInline
value class GPUPointer(
    val address: ULong,
)

class ManagedGPUMemory(val ctx: WebGPUContext, initialSizeBytes: ULong, vararg usage: GPUBufferUsage) : AutoCloseable {
    private var _nextByte = 0uL
    private var currentMemoryLimit = initialSizeBytes
    fun alloc(bytes: ULong): GPUPointer {
        val address = _nextByte
        if (address + bytes > currentMemoryLimit) {
            TODO("Dynamic Memory expansion is not implemented yet")
        }
        _nextByte += bytes
        return GPUPointer(address)
    }

    fun write(pointer: GPUPointer, bytes: FloatArray) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }

    fun write(pointer: GPUPointer, bytes: IntArray) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }

    fun new(data: FloatArray): GPUPointer {
        val pointer = alloc(data.byteSize())
        write(pointer, data)
        return pointer
    }

    fun new(data: IntArray): GPUPointer {
        val pointer = alloc(data.byteSize())
        write(pointer, data)
        return pointer
    }

    val buffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = initialSizeBytes,
            usage = usage.toSet() + setOf(GPUBufferUsage.CopyDst)
        )
    )

    override fun close() {
        buffer.close()
    }
}

private fun FloatArray.byteSize() = (size * Float.SIZE_BYTES).toULong()
private fun IntArray.byteSize() = (size * Int.SIZE_BYTES).toULong()

/**
 * Seperated from [WorldRender] in order to be able to maintain the same [WorldRender] between shader reloads, as the [io.github.natanfudge.fn.render.WorldBindGroups]
 * will be swapped out when that happens.
 */
class WorldBindGroups(
    val world: GPUBindGroup,
    val models: List<GPUBindGroup>,
) : AutoCloseable {
    override fun close() {
        world.close()
        models.forEach { it.close() }
    }
}

class WorldRender(
    val ctx: WebGPUContext,
    //TODO: I don't like this here
    val pipeline: GPURenderPipeline,
    val surface: FunSurface,
) : AutoCloseable {

    val vertexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 1_000_000u, GPUBufferUsage.Vertex)
    val indexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 200_000u, GPUBufferUsage.Index)


    val bindGroup = ctx.device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = listOf(
                BindGroupEntry(
                    binding = 0u,
                    resource = BufferBinding(buffer = surface.uniformBuffer)
                ),
                BindGroupEntry(
                    binding = 1u,
                    resource = surface.sampler
                )
            )
        )
    )
//    fun createBindGroups(): WorldBindGroups {
//        return WorldBindGroups(
//            world = ctx.device.createBindGroup(
//                BindGroupDescriptor(
//                    layout = pipeline.getBindGroupLayout(0u),
//                    entries = listOf(
//                        BindGroupEntry(
//                            binding = 0u,
//                            resource = BufferBinding(buffer = surface.uniformBuffer)
//                        ),
//                        BindGroupEntry(
//                            binding = 1u,
//                            resource = surface.sampler
//                        )
//                    )
//                )
//            ),
//            models = models.map { it.createBindGroup() }
//        )
//    }

    val models = mutableListOf<BoundModel>()
    fun draw(pass: GPURenderPassEncoder) {
        pass.setPipeline(pipeline)
        pass.setBindGroup(0u, bindGroup)
        pass.setVertexBuffer(0u, vertexBuffer.buffer)
        pass.setIndexBuffer(indexBuffer.buffer, GPUIndexFormat.Uint32)
        for (model in models) {
            //TODO: 1. bind a "draw index" uniform that will be later replaced with the builtin draw index,
            // 2. Create an instance indices buffer to point [draw index -> instance index] (chatgpt says to recreate it every frame but i'm not sure about that)
            // 3. Create a big instance buffer, whenever we add a new instance we add to the buffer and update the instance index buffer or smthn
            pass.setBindGroup(1u, model.bindGroup)
            pass.drawIndexed(
                model.model.mesh.indexCount, instanceCount = model.instances, firstIndex = model.firstIndex, baseVertex = model.baseVertex
            )
        }
        pass.end()
    }

    fun bind(model: Model): BoundModel {
        val vertexPointer = vertexBuffer.new(model.mesh.vertices.array)
        val indexPointer = indexBuffer.new(model.mesh.indices.array)
        val bound = BoundModel(
            model, ctx, pipeline,
            // These values are indices, not bytes, so we need to divide by the size of the value
            firstIndex = (indexPointer.address / Int.SIZE_BYTES.toUInt()).toUInt(),
            baseVertex = (vertexPointer.address / VertexArrayBuffer.StrideBytes).toInt()
        )
        models.add(bound)
        return bound
    }

    override fun close() {
        closeAll(vertexBuffer, indexBuffer, bindGroup)
        models.forEach { it.close() }
    }
}

class RenderInstance(pointer: GPUPointer, memory: ManagedGPUMemory) {
    fun despawn() {
        TODO()
    }

    fun setTransform(transform: Mat4f) {
        TODO()
    }

    fun transform(transform: Mat4f) {
        TODO()
    }
}

//fun Model.bind(ctx: WebGPUContext) = BoundModel(this, ctx)

data class Model(val mesh: Mesh, val material: Material = Material()/*, val name: String*/)
class Material(val texture: Image? = null)


//class Struct1<T>