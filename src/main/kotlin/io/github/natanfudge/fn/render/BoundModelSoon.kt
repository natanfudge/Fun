//package io.github.natanfudge.fn.render
//
//import io.github.natanfudge.fn.core.FunId
//import io.github.natanfudge.fn.files.FunImage
//import io.github.natanfudge.fn.render.utils.ManagedGPUMemory
//import io.github.natanfudge.fn.render.utils.toFloatArray
//import io.github.natanfudge.fn.util.closeAll
//import io.github.natanfudge.fn.webgpu.WebGPUContext
//import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
//import io.github.natanfudge.wgpu4k.matrix.Mat4f
//import io.ygdrasil.webgpu.*
//
///**
// * Stores GPU information about all instances of a [Model].
// */
//class BoundModel(
//    val model: Model, private val ctx: WebGPUContext, val firstIndex: UInt, val baseVertex: Int,
//    val world: WorldRender,
//) : AutoCloseable {
//
//    private var currentTexture = model.material.texture
//
//    val textureBuffer = createTextureBuffer(currentTexture)
//
//    init {
//        if (currentTexture != null) {
//            updateTextureBuffer(currentTexture!!)
//        }
//    }
//
//    fun setTexture(newTexture: FunImage) {
//        if (newTexture.size != currentTexture?.size) {
//            this.textureBuffer.close()
//            this.textureView.close()
//        }
//        this.currentTexture = newTexture
//    }
//
//    private fun createTextureBuffer(image: FunImage?): GPUTexture {
//        return ctx.device.createTexture(
//            TextureDescriptor(
//                size = if (image != null) Extent3D(
//                    image.size.width.toUInt(),
//                    image.size.height.toUInt(),
//                    1u
//                ) else Extent3D(1u, 1u),
//                // We loaded srgb data from the png so we specify srgb here. If your data is in a linear color space you can do RGBA8Unorm instead
//                format = GPUTextureFormat.RGBA8UnormSrgb,
//                usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst),
//                label = "Model Texture"
//            )
//        )
//    }
//
//    private fun updateTextureBuffer(image: FunImage) {
//        ctx.device.copyExternalImageToTexture(
//            source = image.bytes,
//            texture = textureBuffer,
//            width = image.size.width, height = image.size.height
//        )
//    }
//
//    val jointCount = if (model.skeleton == null) 0uL else model.skeleton.joints.size.toULong()
//
//    val instanceStruct = JointMatrix(jointCount.toInt())
//
//    /**
//     * Single array per model
//     */
//    val inverseBindMatricesBuffer = ManagedGPUMemory(ctx, initialSizeBytes = Mat4f.SIZE_BYTES * jointCount, expandable = false, GPUBufferUsage.Storage)
//
//
//    init {
//        if (model.skeleton != null) {
//            inverseBindMatricesBuffer.write(
//                model.skeleton.inverseBindMatrices.toFloatArray()
//            )
//        }
//    }
//
//
//    var textureView = textureBuffer.createView()
//
//    val instances = mutableMapOf<FunId, RenderInstance>()
//
//
//    fun spawn(id: FunId, value: Boundable, initialTransform: Mat4f, tint: Tint): RenderInstance {
//        check(id !in instances) { "Instance with id $id already exists" }
//        val instance = world.spawn(id, this, value, initialTransform, tint)
//        instances[id] = instance
//        return instance
//    }
//
//    var bindGroup = createBindGroup(world.pipeline)
//
//    //TODO: need to also recreate and rebind bindgroup
//
//
//    fun recreateBindGroup(pipeline: GPURenderPipeline?) {
//        if (pipeline != null) {
//            this.bindGroup = createBindGroup(pipeline)
//        }
//        // It's gonna get bound automatically otherwise
//    }
//
//
//    private fun createBindGroup(pipeline: GPURenderPipeline) = world.ctx.device.createBindGroup(
//        BindGroupDescriptor(
//            layout = pipeline.getBindGroupLayout(1u),
//            entries = listOf(
//                BindGroupEntry(
//                    binding = 0u,
//                    resource = textureView
//                ),
//                BindGroupEntry(
//                    binding = 1u,
//                    resource = BufferBinding(inverseBindMatricesBuffer.buffer)
//                ),
//            )
//        )
//    )
//
//
//    override fun close() {
//        closeAll(textureBuffer, textureView, inverseBindMatricesBuffer)
//        world.modelBindGroups[model.id]
//    }
//}