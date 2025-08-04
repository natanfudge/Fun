package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.util.*
import io.github.natanfudge.wgpu4k.matrix.Mat4f

data class MovingNode(
    /**
     * The index relative to the list of nodes (includes all joints + the mesh + other possible nodes)
     */
    val nodeIndex: Int,
    /**
     * Changes over time
     */
    val transform: Transform,
)

internal class SkinManager(skeleton: Skeleton, model: Model) {
    internal val jointCount = skeleton.joints.size

    val jointMatrixSize = jointCount.toUInt() * Mat4f.SIZE_BYTES


    private val nodeIndexToJointIndex = buildMap {
        skeleton.joints.forEachIndexed { jointIndex, nodeIndex ->
            put(nodeIndex, jointIndex)
        }
    }

    val nodeTree = model.nodeHierarchy.map {
        MovingNode(it.id, Transform())
    }.toMutableTree()


    /**
     * Returns the joint transforms in model space to pass to the GPU.
     */
    @Suppress("UNCHECKED_CAST")
    fun getModelSpaceJointTransforms(): List<Mat4f> {
        val list = MutableList<Mat4f?>(jointCount) { null }
        nodeTree.visit { (nodeIndex, transform) ->
            val jointIndex = nodeIndexToJointIndex[nodeIndex]
            if (jointIndex != null) {
                list[jointIndex] = transform.toMatrix()
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

            jointTransformEvent.emit(JointTransformEvent(node.nodeIndex, node.transform))
        }
    }

    internal val jointTransformEvent = EventEmitter<JointTransformEvent>()

    init {
        val initialTransform = buildMap {
            model.nodeHierarchy.visit { (id, _, baseTransform) ->
                put(id, baseTransform)
            }
        }
        // Apply initial transform (bind pose)
        applyLocalTransforms(initialTransform)
    }
}

typealias NodeId = Int

internal class JointTransformEvent(
    val joint: NodeId,
    val transform: Transform,
)

/**
 * Map from node index to the local transform to apply to the node.
 * Specifies the interpolated values
 */
typealias SkeletalTransformation = Map<NodeId, Transform>

