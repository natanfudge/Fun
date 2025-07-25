package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.compose.utils.TreeImpl
import io.github.natanfudge.fn.compose.utils.find
import io.github.natanfudge.fn.compose.utils.findNode
import io.github.natanfudge.fn.compose.utils.toList
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.gltf.PartialTransform
import io.github.natanfudge.fn.gltf.fromGlbResourceImpl
import io.github.natanfudge.fn.util.Tree
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.time.Duration

data class ModelNode(
    val id: Int,
    val name: String,
    val baseTransform: Transform,
)

data class Model(
    val mesh: Mesh,
    val id: ModelId,
    val material: Material = Material(),
    val animations: List<Animation> = listOf(),
    /** References nodes by nodeIndex.  **/
    val nodeHierarchy: Tree<ModelNode> = TreeImpl(ModelNode(0, "", baseTransform = Transform()), listOf()),
    val skeleton: Skeleton? = null,
) {
    fun getAnimationLength(name: String, withLastFrameTrimmed: Boolean): Duration {
        val animation = animations.find { it.name == name } ?: error("Cannot find animation with name '$name'")
        if (withLastFrameTrimmed) {
            return animation.keyFrames[animation.keyFrames.size - 2].time
        } else {
            return animation.keyFrames.last().time
        }
    }

    fun nodeAndChildren(nodeName: String): List<String> {
        val parentNode = nodeHierarchy.findNode { it.name == nodeName } ?: error("Cannot find node with name '$nodeName'")
        return parentNode.toList().map { it.name }
    }


    fun nodesAndTheirChildren(vararg nodeNames: String): List<String>  = nodeNames.flatMap { nodeAndChildren(it) }

    val baseRootTransform = nodeHierarchy.value.baseTransform.toMatrix()

    companion object {
        fun fromGlbResource(path: String): Model = modelCache.getOrPut(path) {
            Model.fromGlbResourceImpl(path)
        }
        fun quad(imagePath: String) = modelCache.getOrPut(imagePath) {
            Model(Mesh.UnitSquare, imagePath, material = Material(FunImage.fromResource(imagePath)))
        }
    }
}

//SUS: gonna do this more robust and not global state when we have proper async model loading.
internal val modelCache = mutableMapOf<String, Model>()

fun clearModelCache() = modelCache.clear()




data class Transform(
    val translation: Vec3f = Vec3f.zero(),
    val rotation: Quatf = Quatf.identity(),
    val scale: Vec3f = Vec3f(1f, 1f, 1f),
) {

    /**
     * Important note: [dst] should be different from `this` and [other]!
     */
    fun mul(other: Transform, dst: Transform = Transform()): Transform {
        require(dst !== this && dst !== other)

        // 1️⃣ combined scale
        scale.mul(other.scale, dst.scale)


        // 2️⃣ combined rotation (apply `other` then `this`)
        rotation.mul(other.rotation, dst.rotation)


        // 3️⃣ combined translation
        //    step A: scale `other`’s translation by this scale
        val temp = Vec3f()
        other.translation.mul(scale, temp)
        //    step B: rotate it by this rotation
        rotation.rotate(temp, temp)
        //    step C: finally add this translation
        temp.add(translation, dst.translation)
        return dst
    }

    companion object {
        fun fromMatrix(matrix: Mat4f): Transform {
            return Transform(matrix.getTranslation(), error(" to do"), error(""))
        }
    }

    fun toMatrix() = Mat4f.translateRotateScale(translation, rotation, scale)
    fun set(transform: Transform) {
        translation.set(transform.translation)
        rotation.set(transform.rotation)
        scale.set(transform.scale)
    }


}

/**
 * Does not specify interpolated values, it assumes you will infer them from other keyframes
 */
typealias PartialSkeletonTransform = Map<Int, PartialTransform>

data class KeyFrame(
    val time: Duration,
    val transform: PartialSkeletonTransform,
)

data class Animation(
    val name: String,
    /**
     * Sorted by time
     */
    val keyFrames: List<KeyFrame>,
) {
    val affectedJoints by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val joints = mutableSetOf<Int>()
        for (keyFrame in keyFrames) {
            joints.addAll(keyFrame.transform.keys)
        }
        joints
    }
}

data class Joint(
    /**
     * An index associated with any animatable thing, such as a joint or the mesh itself.
     */
    val nodeIndex: Int,
    val baseTransform: Transform,
)


data class Skeleton(
    /**
     * List of node indices referring to joint nodes
     */
    val joints: List<Int>,
    val inverseBindMatrices: List<Mat4f>,
)


typealias ModelId = String

data class Material(val texture: FunImage? = null) {

}