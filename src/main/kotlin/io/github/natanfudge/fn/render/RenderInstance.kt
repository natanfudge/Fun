@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.render

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.render.utils.GPUPointer
import io.github.natanfudge.fn.util.map
import io.github.natanfudge.fn.util.visit
import io.github.natanfudge.fn.util.visitWithParent
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f


data class MovingNode(
    /**
     * The index relative to the list of nodes (includes all joints + the mesh + other possible nodes)
     */
    val nodeIndex: Int,
    /**
     * Changes over time
     */
    val transform: Mat4f,
)

private class SkinManager(skeleton: Skeleton, model: Model) {
    private val jointCount = skeleton.joints.size

    val jointMatrixSize = jointCount.toUInt() * Mat4f.SIZE_BYTES


    private val nodeIndexToJointIndex = buildMap {
        skeleton.joints.forEachIndexed { jointIndex, nodeIndex ->
            put(nodeIndex, jointIndex)
        }
    }

    val nodeTree = model.nodeHierarchy.map {
        MovingNode(it.id, Mat4f())
    }


    /**
     * Returns the joint transforms in model space to pass to the GPU.
     */
    @Suppress("UNCHECKED_CAST")
    fun getModelSpaceJointTransforms(): List<Mat4f> {
        val list = MutableList<Mat4f?>(jointCount) { null }
        nodeTree.visit { (nodeIndex, transform) ->
            val jointIndex = nodeIndexToJointIndex[nodeIndex]
            if (jointIndex != null) {
                list[jointIndex] = transform
            }
        }
        // This process is supposed to fill up the list completely
        return list as List<Mat4f>
    }

    /**
     * Replaces the local transformations of the joints with the ones in the given [jointTransforms].
     * Two important distinctions:
     * 1. This is in **local** space, meaning each bone relative to its parent
     * 2. This **overwrites** the transform, it does not multiply by the existing transform.
     */
    fun applyLocalTransforms(jointTransforms: SkeletalTransformation) {
        nodeTree.visitWithParent { parent, node ->                     // level-order walk
            // Important: if no transform is specified, we use the base transform (the bind pose)
            val local = jointTransforms[node.nodeIndex]
                ?: error("applyLocalTransforms should be passed a transform for ALL $jointCount joints, (${jointTransforms.size} specified), either specifying the bind pose transform or an interpolated transform")


            if (parent == null) {
                node.transform.set(local)               // root: M_model = M_local
            } else {
                parent.transform.mul(local, node.transform)
            }
        }
    }

    init {
        val initialTransform = buildMap {
            model.nodeHierarchy.visit { (id, baseTransform) ->
                put(id, baseTransform.toMatrix())
            }
        }
        // Apply initial transform (bind pose)
        applyLocalTransforms(initialTransform)
    }
}

/**
 * Map from node index to the local transform to apply to the node.
 * Specifies the interpolated values
 */
typealias SkeletalTransformation = Map<Int, Mat4f>


class RenderInstance(
    val funId: FunId,
    initialTransform: Mat4f,
    initialTint: Tint,
    val bound: BoundModel,
    @PublishedApi internal val world: WorldRender,
    var value: Boundable,
) : Boundable {
    // SLOW: should reconsider passing normal matrices always
    private val normalMatrix = Mat3f.normalMatrix(initialTransform)
    internal val globalInstancePointer = world.baseInstanceData.newInstance(
        BaseInstanceData.toArray(
            initialTransform, normalMatrix, initialTint.color, initialTint.strength, if (bound.image == null) 0 else 1,
            if (bound.model.skeleton == null) 0 else 1
        )
    ) as GPUPointer<BaseInstanceData>


    private val skin = if (bound.model.skeleton == null) null else SkinManager(bound.model.skeleton, bound.model)

    internal val jointMatricesPointer = world.jointMatrixData.newInstance(
        bound.instanceStruct.toArray(
            skin?.getModelSpaceJointTransforms()
            // Note: don't need this when we have proper feature separation
                ?: listOf()
        )
    ) as GPUPointer<JointMatrix>


    // The index is a pointer to the instance in the instanceBuffer array (wgpu indexes a struct of arrays, so it needs an index, not a pointer)
    val renderId = (globalInstancePointer.address / BaseInstanceData.size).toInt()

    override val boundingBox: AxisAlignedBoundingBox
        get() = value.boundingBox

    private var setTransform: Mat4f = initialTransform
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
        world.baseInstanceData.free(globalInstancePointer, BaseInstanceData.size)
        if (skin != null) {
            world.jointMatrixData.free(jointMatricesPointer, skin.jointMatrixSize)
        }
        despawned = true
    }

    private fun checkDespawned() {
        if (despawned) throw IllegalStateException("Attempt to transform despawned object")
    }


    /**
     * Called once per frame to apply any changes made to the instance values.
     */
    internal fun updateGPU() {
        val instanceDataBuffer = world.baseInstanceData.instanceBuffer
        if (requestedTransform != null) {
            // Without a skin, we just apply the model's root transform directly here, and with a skin the root transform is applied by the skin matrix,
            // since the joints are considered children nodes of the root node.
            val gpuTransform = if (skin == null) requestedTransform!! * bound.model.baseRootTransform else requestedTransform!!
            // Setting all values at once is faster than setting two values individually
            BaseInstanceData.set(
                instanceDataBuffer, globalInstancePointer,
                gpuTransform, Mat3f.normalMatrix(requestedTransform!!), gpuTintColor, gpuTintStrength,
                if (bound.image == null) 0 else 1,
                if (bound.model.skeleton == null) 0 else 1
            )
            setTransform = requestedTransform!!
            requestedTransform = null
        }
        if (requestedTintColor != null) {
            BaseInstanceData.setThird(instanceDataBuffer, globalInstancePointer, requestedTintColor!!)
            gpuTintColor = requestedTintColor!!
            requestedTintColor = null
        }
        if (requestedTintStrength != null) {
            BaseInstanceData.setFourth(instanceDataBuffer, globalInstancePointer, requestedTintStrength!!)
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
            world.jointMatrixData.instanceBuffer,
            jointMatricesPointer,
            skin.getModelSpaceJointTransforms()
        )
    }

    fun setTransform(transform: Mat4f) {
        checkDespawned()
        if (transform != setTransform) {
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