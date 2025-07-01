package io.github.natanfudge.fn.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.lightPos
import io.github.natanfudge.fn.network.ColorSerializer
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.*
import kotlinx.serialization.Serializable


//interface BoundModel {
//    fun spawn(id: FunId, value: Renderable, tint: Tint): RenderInstance
//}

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

    val instances = mutableMapOf<FunId, RenderInstance>()


    fun spawn(id: FunId, value: Boundable, initialTransform: Mat4f, tint: Tint): RenderInstance {
        check(id !in instances) { "Instance with id $id already exists" }
        val instance = world.spawn(id, this, value, initialTransform, tint)
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


/**
 *   initialTransform, normalMatrix, tint.color, tint.strength, if (model.image == null) 0 else 1
 */
object GPUInstance : Struct6<Mat4f, Mat3f, Color, Float, Int, Int, GPUInstance>(Mat4fDT, Mat3fDT, ColorDT, FloatDT, IntDT, IntDT)

object WorldUniform : Struct5<Mat4f, Vec3f, Vec3f, UInt, UInt, WorldUniform>(
    Mat4fDT, Vec3fDT, Vec3fDT, UIntDT, UIntDT
)

class WorldRender(
    val ctx: WebGPUContext,
    val surface: FunSurface,
) : AutoCloseable {

//    private val instanceIndexProvider = ConsecutiveIndexProvider()

    val uniformBuffer = WorldUniform.createBuffer(ctx, 1u, expandable = false, GPUBufferUsage.Uniform)

    val rayCasting = RayCastingCache<RenderInstance>()
    var selectedObjectId: Int = -1

    var hoveredObject: Boundable? by mutableStateOf(null)

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
    val models = mutableMapOf<ModelId, BoundModel>()

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

    private fun createModelBindGroup(pipeline: GPURenderPipeline, model: BoundModel) = ctx.device.createBindGroup(
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

        // SLOW: should only run this when the buffer is invalidated
        rebuildInstanceIndexBuffer()
        pass.setPipeline(pipeline)
        pass.setBindGroup(0u, worldBindGroup)
        pass.setVertexBuffer(0u, vertexBuffer.buffer)
        pass.setIndexBuffer(indexBuffer.buffer, GPUIndexFormat.Uint32)

        var instanceIndex = 0u
        for ((i, model) in models.values.withIndex()) {
            for (instance in model.instances) {
                instance.value.updateGPU()
            }

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
            // If pipeline is null, the modelBindGroups will be built once it is not null
            modelBindGroups.add(createModelBindGroup(pipeline!!, bound))
        }
        return bound
    }

    private val instanceBufferElementCount get() = GPUInstance.fullElements(instanceBuffer).toInt()

    /**
     * Note: `model.instances` is updated by [VisibleBoundModel.spawn]
     */
    fun spawn(id: FunId, model: BoundModel, value: Boundable, initialTransform: Mat4f, tint: Tint): RenderInstance {
        // SLOW: should reconsider passing normal matrices always
        val normalMatrix = Mat3f.normalMatrix(initialTransform)
        val pointer = GPUInstance.new(
            instanceBuffer, initialTransform, normalMatrix, tint.color, tint.strength, if (model.image == null) 0 else 1,
            if (model.model.animations.isEmpty()) 0 else 1
        )

        // The index is a pointer to the instance in the instanceBuffer array (wgpu indexes a struct of arrays, so it needs an index, not a pointer)
        val instanceIndex = (pointer.address / GPUInstance.size).toInt()

        val instance = RenderInstance(pointer, instanceIndex, id, initialTransform, tint, model, this, value)
        rayCasting.add(instance)

        indexBufferInvalid = true

        return instance
    }

    /**
     * The index buffer is invalid when new instances are added or removed. This is usually not every frame,
     * so we recreate the index buffer only when that happens (we set this to true, and on render we recreate the buffer and set this to false)
     */
    private var indexBufferInvalid = true

    private fun rebuildInstanceIndexBuffer() {
        if (indexBufferInvalid) {
            indexBufferInvalid = false
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

    }

    internal fun remove(renderInstance: RenderInstance) {
        renderInstance.model.instances.remove(renderInstance.funId)
        instanceBuffer.free(renderInstance.pointer, GPUInstance.size)
        rayCasting.remove(renderInstance)
        indexBufferInvalid = true
    }

    override fun close() {
        closeAll(vertexBuffer, indexBuffer, instanceBuffer, instanceIndexBuffer, uniformBuffer)
        modelBindGroups.forEach { it.close() }
        models.forEach { it.value.close() }
    }
}

@Serializable
data class Tint(val color: @Serializable(with = ColorSerializer::class) Color, val strength: Float = 0.5f)


//interface RenderInstance {
//
//    fun setTransform(transform: Mat4f)
//
//    fun setTintColor(color: Color)
//
//    fun setTintStrength(strength: Float)
//
//    fun despawn()
//}

class RenderInstance(
    @PublishedApi internal val pointer: GPUPointer<GPUInstance>,
    internal val renderId: Int,
    val funId: FunId,
    initialTransform: Mat4f,
    initialTint: Tint,
//    val value: T,
    val model: BoundModel,
    @PublishedApi internal val world: WorldRender,
    var value: Boundable,
) : Boundable {
    override val boundingBox: AxisAlignedBoundingBox
        get() = value.boundingBox

    private var gpuTransform: Mat4f = initialTransform
    private var gpuTintColor: Color = initialTint.color
    private var gpuTintStrength: Float = initialTint.strength

    // Optimization: only update GPU once per frame, store requested changes in memory and update before frame.
    private var requestedTransform: Mat4f? = null
    private var requestedTintColor: Color? = null
    private var requestedTintStrength: Float? = null

    var despawned = false

    /**
     * Removes this instance from the world, cleaning up any held resources.
     * After despawning, attempting to transform this instance will fail.
     */
    fun despawn() {
        world.remove(this)
        despawned = true
    }

    private fun checkDespawned() {
        if (despawned) throw IllegalStateException("Attempt to transform despawned object")
    }

    /**
     * Called once per frame to apply any changes made to the instance values.
     */
    internal fun updateGPU() {
        if (requestedTransform != null) {
            // Setting all values at once is faster than setting two values individually
            GPUInstance.set(
                world.instanceBuffer, pointer,
                requestedTransform!!, Mat3f.normalMatrix(requestedTransform!!), gpuTintColor, gpuTintStrength,
                if (model.image == null) 0 else 1,
                if (model.model.animations.isEmpty()) 0 else 1
            )
//            GPUInstance.setFirst(world.instanceBuffer, pointer, requestedTransform!!)
//            // Update normal matrix, as the transform changed
//            GPUInstance.setSecond(world.instanceBuffer, pointer, Mat3f.normalMatrix(requestedTransform!!))
            gpuTransform = requestedTransform!!
            requestedTransform = null
        }
        if (requestedTintColor != null) {
            GPUInstance.setThird(world.instanceBuffer, pointer, requestedTintColor!!)
            gpuTintColor = requestedTintColor!!
            requestedTintColor = null
        }
        if (requestedTintStrength != null) {
            GPUInstance.setFourth(world.instanceBuffer, pointer, requestedTintStrength!!)
            gpuTintStrength = requestedTintStrength!!
            requestedTintStrength = null
        }
    }

    fun setTransform(transform: Mat4f) {
        checkDespawned()
        if (transform != gpuTransform) {
            this.requestedTransform = transform
        } else {
            // Want to go back to the initial value? just don't do anything!
            this.requestedTransform = null
        }
    }

    fun setTintColor(color: Color) {
        checkDespawned()
        if (color != gpuTintColor) {
            this.requestedTintColor = color
        } else {
            this.requestedTintColor = null
        }
    }

    fun setTintStrength(strength: Float) {
        checkDespawned()
        if (strength != gpuTintStrength) {
            this.requestedTintStrength = strength
        } else {
            this.requestedTintStrength = null
        }
    }
}

data class Transform(
    val translation: Vec3f = Vec3f.zero(),
    val rotation: Quatf = Quatf.identity(),
    var scale: Vec3f = Vec3f(1f, 1f, 1f),
)

object Animation


data class Model(
    val mesh: Mesh,
    val id: ModelId,
    val material: Material = Material(),
    val initialTransform: Transform = Transform(),
    val animations: List<Animation> = listOf(),
) {
    companion object;
}

typealias ModelId = String

class Material(val texture: FunImage? = null)


