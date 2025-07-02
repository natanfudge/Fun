package io.github.natanfudge.fn.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.lightPos
import io.github.natanfudge.fn.network.ColorSerializer
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.util.*
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
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

    val jointCount = if (model.skeleton == null) 0uL else model.skeleton.joints.size.toULong()

    val instanceStruct = ModelInstance(jointCount.toInt())

    val maxInstanceCount = 1000u

    //SLOW: should have one huge buffer, not one buffer per model.
    /** Single array per instance, and this stores the joint matrices for the entire model, so this has multiple arrays */
    val jointsMatricesBuffer = instanceStruct.createBuffer(ctx, maxInstanceCount, expandable = false, GPUBufferUsage.Storage)

    /**
     * Single array per model
     */
    val inverseBindMatricesBuffer = ManagedGPUMemory(ctx, initialSizeBytes = Mat4f.SIZE_BYTES * jointCount, expandable = false, GPUBufferUsage.Storage)

    val uniformBuffer = ModelUniform.createBuffer(ctx, initialSize = 1u, expandable = false, GPUBufferUsage.Uniform)


    //TODO: initialize joint matrices with initial transform: hierarchical based on transform value

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
                    resource = BufferBinding(jointsMatricesBuffer.buffer)
                ),
                BindGroupEntry(
                    binding = 2u,
                    resource = BufferBinding(inverseBindMatricesBuffer.buffer)
                ),
                BindGroupEntry(
                    binding = 3u,
                    resource = BufferBinding(uniformBuffer.buffer)
                )
            )
        )
    )


    override fun close() {
        closeAll(texture, textureView, jointsMatricesBuffer, inverseBindMatricesBuffer, uniformBuffer)
    }
}


/**
 *   initialTransform, normalMatrix, tint.color, tint.strength, if (model.image == null) 0 else 1
 */
object GlobalInstance : Struct6<Mat4f, Mat3f, Color, Float, Int, Int, GlobalInstance>(Mat4fDT, Mat3fDT, ColorDT, FloatDT, IntDT, IntDT)

/**
 * Stores the information specific to an instance, with a size that changes between each model
 *
 * In contrast, [GlobalInstance] stores information per instance that is constant in size
 *
 *  */
/////////////////                       joint_matrices
class ModelInstance(jointCount: Int) : Struct1<List<Mat4f>, ModelInstance>(Mat4fArrayDT(jointCount))

//                      joint_matrices_stride
object ModelUniform : Struct2<Int, Int, ModelUniform>(IntDT, IntDT)

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

    // SUS: I made this humongously big because I didn't implement freeing yet, so when we restart the app the instance buffer keeps filling up.
    // We should strive to make this smaller afterwards, to identify memory leaks (obv now we have a mega memory leak)
    private val maxIndices = 50000u

    val instanceBuffer = GlobalInstance.createBuffer(ctx, maxIndices, expandable = false, GPUBufferUsage.Storage)

    /**
     * Since instances are not stored contiguously in memory per mesh, we need a separate buffer that IS contiguous per mesh to point each instance
     * to its correct location in the instance buffer.
     */
    val instanceIndexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = Int.SIZE_BYTES.toULong() * maxIndices, expandable = false, GPUBufferUsage.Storage)

    //    val models = mutableListOf<BoundModel>()
    val models = mutableMapOf<ModelId, BoundModel>()

    //    var bindGroup =
    val modelBindGroups = mutableMapOf<ModelId, GPUBindGroup>()


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

        rebuildInstanceIndexBuffer()
        pass.setPipeline(pipeline)
        pass.setBindGroup(0u, worldBindGroup)
        pass.setVertexBuffer(0u, vertexBuffer.buffer)
        pass.setIndexBuffer(indexBuffer.buffer, GPUIndexFormat.Uint32)

        var instanceIndex = 0u
        for (model in models.values) {
            // SLOW: This should be replaced by an index buffer similar to the one used for the fixed size data

            //TODO: something is still wrong, the second model is getting sucked into the void and looks weird in general
            ModelUniform.set(model.uniformBuffer, GPUPointer(0u), model.jointCount.toInt(), instanceIndex.toInt())

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

    private val instanceBufferElementCount get() = GlobalInstance.fullElements(instanceBuffer).toInt()

    /**
     * Note: `model.instances` is updated by [VisibleBoundModel.spawn]
     */
    fun spawn(id: FunId, model: BoundModel, value: Boundable, initialTransform: Mat4f, tint: Tint): RenderInstance {


        val instance = RenderInstance(id, initialTransform, tint, model, this, value)
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
        renderInstance.bound.instances.remove(renderInstance.funId)
        instanceBuffer.free(renderInstance.globalInstancePointer, GlobalInstance.size)
        rayCasting.remove(renderInstance)
        indexBufferInvalid = true
    }

    override fun close() {
        closeAll(vertexBuffer, indexBuffer, instanceBuffer, instanceIndexBuffer, uniformBuffer)
        modelBindGroups.values.forEach { it.close() }
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

 data class MovingJoint(
    /**
     * The index relative to the list of joints
     */
    val jointIndex: Int,
    /**
     * The index relative to the list of nodes (includes all joints + the mesh + other possible nodes)
     */
    val nodeIndex: Int,
    /**
     * Changes over time
     */
    val transform: Mat4f,
)

private class SkinManager(skeleton: Skeleton) {
    private val jointCount = skeleton.joints.size

    // SLOW: No need to calculate this one per instance
    val nodeIndexToJointWithBaseTransform = skeleton.joints.mapIndexed { i, joint ->
        joint.nodeIndex to MovingJoint(jointIndex = i, nodeIndex = joint.nodeIndex, transform = joint.baseTransform)
    }.toMap()
    val jointTree = skeleton.hierarchy.map {
        // The transform is gonna get overwritten so it doesn't matter
        val joint =nodeIndexToJointWithBaseTransform.getValue(it)
        joint.copy(transform = Mat4f.identity())
    }

    /**
     * Returns the joint transforms in model space to pass to the GPU.
     */
    @Suppress("UNCHECKED_CAST")
    fun getModelSpaceTransforms(): List<Mat4f> {
        val list = MutableList<Mat4f?>(jointCount) { null }
        jointTree.visit { (jointIndex,_, transform) ->
            list[jointIndex] = transform
        }
        // This process is supposed to fill up the list completely
        return list as List<Mat4f>
    }

    //TODO: apparently the problem is that I'm supposed to use the interoplated values, and the gltf sampler does the interpolation for you.

    /**
     * Replaces the local transformations of the joints with the ones in the given [jointTransforms].
     * Two important distinctions:
     * 1. This is in **local** space, meaning each bone relative to its parent
     * 2. This **overwrites** the transform, it does not multiply by the existing transform.
     */
    fun applyLocalTransforms(jointTransforms: SkeletalTransformation) {
        jointTree.visitWithParent { parent, node ->                     // level-order walk
            // Important: if no transform is specified, we use the base transform (the bind pose)
            val local = jointTransforms[node.nodeIndex] ?: nodeIndexToJointWithBaseTransform.getValue(node.nodeIndex).transform

            if (parent == null) {
                node.transform.set(local)               // root: M_model = M_local
            } else {
                parent.transform.mul(local, node.transform)
            }
        }
    }

    init {
        // Apply initial transform (bind pose)
        applyLocalTransforms(mutableMapOf())
    }
}

/**
 * Map from node index to the local transform to apply to the node.
 */
typealias SkeletalTransformation = Map<Int, Mat4f>


class RenderInstance(
//    internal val renderId: Int,
    val funId: FunId,
    initialTransform: Mat4f,
    initialTint: Tint,
//    val value: T,
    val bound: BoundModel,
    @PublishedApi internal val world: WorldRender,
    var value: Boundable,
) : Boundable {
    // SLOW: should reconsider passing normal matrices always
    private val normalMatrix = Mat3f.normalMatrix(initialTransform)
    internal val globalInstancePointer = GlobalInstance.new(
        world.instanceBuffer, initialTransform, normalMatrix, initialTint.color, initialTint.strength, if (bound.image == null) 0 else 1,
        if (bound.model.skeleton == null) 0 else 1
    )

    private val skin = if (bound.model.skeleton == null) null else SkinManager(bound.model.skeleton)


    private val jointMatricesPointer = bound.instanceStruct.new(
        bound.jointsMatricesBuffer,
        skin?.getModelSpaceTransforms()
        // Note: don't need this when we have proper feature separation
            ?: listOf()
    )


    // The index is a pointer to the instance in the instanceBuffer array (wgpu indexes a struct of arrays, so it needs an index, not a pointer)
    val renderId = (globalInstancePointer.address / GlobalInstance.size).toInt()

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

//    fun setAnimationFrame()

    private fun checkDespawned() {
        if (despawned) throw IllegalStateException("Attempt to transform despawned object")
    }


    /**
     * Called once per frame to apply any changes made to the instance values.
     */
    internal fun updateGPU() {
        if (requestedTransform != null) {
            // Setting all values at once is faster than setting two values individually
            GlobalInstance.set(
                world.instanceBuffer, globalInstancePointer,
                requestedTransform!!, Mat3f.normalMatrix(requestedTransform!!), gpuTintColor, gpuTintStrength,
                if (bound.image == null) 0 else 1,
                if (bound.model.skeleton == null) 0 else 1
            )
//            GPUInstance.setFirst(world.instanceBuffer, pointer, requestedTransform!!)
//            // Update normal matrix, as the transform changed
//            GPUInstance.setSecond(world.instanceBuffer, pointer, Mat3f.normalMatrix(requestedTransform!!))
            gpuTransform = requestedTransform!!
            requestedTransform = null
        }
        if (requestedTintColor != null) {
            GlobalInstance.setThird(world.instanceBuffer, globalInstancePointer, requestedTintColor!!)
            gpuTintColor = requestedTintColor!!
            requestedTintColor = null
        }
        if (requestedTintStrength != null) {
            GlobalInstance.setFourth(world.instanceBuffer, globalInstancePointer, requestedTintStrength!!)
            gpuTintStrength = requestedTintStrength!!
            requestedTintStrength = null
        }
    }

    fun setJointTransforms(transforms: SkeletalTransformation) {
        checkDespawned()
        val skin = skin ?: throw UnallowedFunException("setJointTransforms is not relevant for a model without a skin '${bound.model.id}'")
        skin.applyLocalTransforms(transforms)
        // Just apply it to the GPU right away, I don't think anyone will try to call this multiple times a frame.
        bound.instanceStruct.setFirst(
            bound.jointsMatricesBuffer,
            jointMatricesPointer,
            skin.getModelSpaceTransforms()
        )
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

