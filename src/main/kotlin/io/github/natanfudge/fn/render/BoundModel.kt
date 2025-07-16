package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.render.utils.ManagedGPUMemory
import io.github.natanfudge.fn.render.utils.toFloatArray
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.TextureDescriptor

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
            usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst),
            label = "Model Texture"
        )
    )

    fun setTexture(newTexture: FunImage) {
        this.texture.close()
        this.textureView.close()

    }

    val jointCount = if (model.skeleton == null) 0uL else model.skeleton.joints.size.toULong()

    val instanceStruct = JointMatrix(jointCount.toInt())

    /**
     * Single array per model
     */
    val inverseBindMatricesBuffer = ManagedGPUMemory(ctx, initialSizeBytes = Mat4f.SIZE_BYTES * jointCount, expandable = false, GPUBufferUsage.Storage)


    init {
        if (image != null) {
            ctx.device.copyExternalImageToTexture(
                source = image.bytes,
                texture = texture,
                width = image.width, height = image.height
            )
        }
        if (model.skeleton != null) {
            inverseBindMatricesBuffer.write(
                model.skeleton.inverseBindMatrices.toFloatArray()
            )
        }
    }


    var textureView = texture.createView()

    val instances = mutableMapOf<FunId, RenderInstance>()


    fun spawn(id: FunId, value: Boundable, initialTransform: Mat4f, tint: Tint): RenderInstance {
        check(id !in instances) { "Instance with id $id already exists" }
        val instance = world.spawn(id, this, value, initialTransform, tint)
        instances[id] = instance
        return instance
    }
    //TODO: need to also recreate and rebind bindgroup

    internal fun createBindGroup(pipeline: GPURenderPipeline) = world.ctx.device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(1u),
            entries = listOf(
                BindGroupEntry(
                    binding = 0u,
                    resource = textureView
                ),
                BindGroupEntry(
                    binding = 1u,
                    resource = BufferBinding(inverseBindMatricesBuffer.buffer)
                ),
            )
        )
    )


    override fun close() {
        closeAll(texture, textureView, inverseBindMatricesBuffer)
    }
}