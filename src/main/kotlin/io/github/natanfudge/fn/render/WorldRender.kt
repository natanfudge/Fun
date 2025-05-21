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

/**
 * Stores GPU information about all instances of a [Model].
 */
class BoundModel(val model: Model, ctx: WebGPUContext, val pipeline: GPURenderPipeline) : AutoCloseable {
    val device = ctx.device

    // SLOW: with i need to combine the buffers, esp with multiDrawIndirect
    val vertexBuffer = device.createBuffer(
        BufferDescriptor(
            size = model.mesh.verticesByteSize,
            usage = setOf(GPUBufferUsage.Vertex, GPUBufferUsage.CopyDst),
        )
    )

    val indexBuffer = device.createBuffer(
        BufferDescriptor(
            size = model.mesh.indexCount * Float.SIZE_BYTES.toULong(),
            usage = setOf(GPUBufferUsage.Index, GPUBufferUsage.CopyDst),
        )
    )
    val instanceBuffer = GPUManagedMemory(ctx, initialSizeBytes = (instanceBytes * 10u))

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

    init {
        device.queue.writeBuffer(vertexBuffer, 0u, model.mesh.vertices.array)
        device.queue.writeBuffer(indexBuffer, 0u, model.mesh.indices.array)
    }

    fun draw(pass: GPURenderPassEncoder) {
        pass.setBindGroup(1u, bindGroup)
        pass.setVertexBuffer(0u, vertexBuffer)
        pass.setIndexBuffer(indexBuffer, GPUIndexFormat.Uint32)
        pass.drawIndexed(model.mesh.indexCount, instanceCount = instances)
    }

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
        closeAll(vertexBuffer, indexBuffer, instanceBuffer, texture, textureView, bindGroup)
    }
}

private fun Color.toFloatArray() = floatArrayOf(red, green, blue, alpha)

@JvmInline
value class GPUPointer(
    val address: ULong,
)

class GPUManagedMemory(val ctx: WebGPUContext, initialSizeBytes: ULong) : AutoCloseable {
    private var nextByte = 0uL
    fun alloc(bytes: ULong): GPUPointer {
        val address = nextByte
        nextByte += bytes
        return GPUPointer(address)
    }

    fun write(pointer: GPUPointer, bytes: FloatArray) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }

    val buffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = initialSizeBytes,
            usage = setOf(GPUBufferUsage.Storage, GPUBufferUsage.CopyDst)
        )
    )

    override fun close() {
        buffer.close()
    }
}

class WorldRender(
    val ctx: WebGPUContext,
    //TODO: I don't like this here
    val pipeline: GPURenderPipeline,
    val surface: FunSurface,
) : AutoCloseable {
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
    private val models = mutableListOf<BoundModel>()
    fun draw(pass: GPURenderPassEncoder) {
        pass.setPipeline(pipeline)
        pass.setBindGroup(0u, bindGroup)
        for (model in models) {
            model.draw(pass)
        }
        pass.end()
    }

    fun bind(model: Model): BoundModel {
        val bound = BoundModel(model, ctx, pipeline)
        models.add(bound)
        return bound
    }

    override fun close() {
        bindGroup.close()
        models.forEach { it.close() }
    }
}

class RenderInstance(pointer: GPUPointer, memory: GPUManagedMemory) {
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

data class Model(val mesh: Mesh, val material: Material = Material())
class Material(val texture: Image? = null)