@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.render

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.compose.utils.find
import io.github.natanfudge.fn.error.UnallowedFunException
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.physics.Transformable
import io.github.natanfudge.fn.render.utils.GPUPointer
import io.github.natanfudge.fn.util.Listener
import io.github.natanfudge.fn.util.cast
import io.github.natanfudge.fn.util.compose
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f


class RenderInstance internal constructor(
    val funId: FunId,
    initialTransform: Mat4f,
    initialTint: Tint,
    val bound: BoundModel,
    private val instanceBuffer: IndirectInstanceBuffer,
    private val jointBuffer: IndirectInstanceBuffer,
//    @PublishedApi internal val world: WorldRender,
    var value: Boundable,
    val onClose: (RenderInstance) -> Unit
) : Boundable, AutoCloseable {

    override fun close() {
        onClose(this)
    }

    // SLOW: should reconsider passing normal matrices always
    private val normalMatrix = Mat3f.normalMatrix(initialTransform)
    internal val globalInstancePointer = instanceBuffer.newInstance(
        BaseInstanceData.toArray(
            initialTransform, normalMatrix, initialTint.color, initialTint.strength, if (bound.currentTexture == null) 0 else 1,
            if (bound.model.skeleton == null) 0 else 1
        )
    ) as GPUPointer<BaseInstanceData>



    internal val skin = if (bound.model.skeleton == null) null else SkinManager(bound.model.skeleton, bound.model)

    /**
     * Allows following the transform of the given node [jointNodeId]
     */
    fun jointTransform(worldTransform: Transformable, jointNodeId: NodeId): Transformable {
        return JointTransform(
            jointNodeId, skin ?: throw UnallowedFunException("jointTransform must only be called for models with a skeleton!"),
            worldTransform
        )
    }

    internal val jointMatricesPointer = jointBuffer.newInstance(
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



    fun setTexture(texture: FunImage) {
        bound.setTexture(texture)
    }

    private fun checkDespawned() {
        if (despawned) throw IllegalStateException("Attempt to transform despawned object")
    }


    /**
     * Called once per frame to apply any changes made to the instance values.
     */
    internal fun updateGPU() {
        val instanceDataBuffer = instanceBuffer.instanceBuffer
        if (requestedTransform != null) {
            // Without a skin, we just apply the model's root transform directly here, and with a skin the root transform is applied by the skin matrix,
            // since the joints are considered children nodes of the root node.
            val gpuTransform = if (skin == null) requestedTransform!! * bound.model.baseRootTransform else requestedTransform!!
            // Setting all values at once is faster than setting two values individually
            BaseInstanceData.set(
                instanceDataBuffer, globalInstancePointer,
                gpuTransform, Mat3f.normalMatrix(requestedTransform!!), gpuTintColor, gpuTintStrength,
                if (bound.currentTexture == null) 0 else 1,
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
            jointBuffer.instanceBuffer,
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

/**
 * Follows the transformation of a specific joint
 */
private class JointTransform(val jointId: Int, val skinManager: SkinManager, val worldTransform: Transformable) : Transformable {
    private val node = skinManager.nodeTree.find { it.nodeIndex == jointId } ?: error("Cannot find joint with id ${jointId} in node tree")

    override var transform: Transform = worldTransform.transform.mul(node.transform)

    override fun onTransformChange(callback: (Transform) -> Unit): Listener<Transform> {
        val localListener = skinManager.jointTransformEvent.listenUnscoped {
            if (it.joint == jointId) {
                transform = worldTransform.transform.mul(it.transform)
                callback(transform)
            }
        }
        val parentListener = worldTransform.onTransformChange {
            transform = it.mul(node.transform)
            callback(transform)
        }
        return localListener.compose(parentListener).cast()
    }
}