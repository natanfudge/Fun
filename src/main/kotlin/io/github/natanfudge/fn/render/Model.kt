package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.compose.utils.TreeImpl
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.gltf.PartialTransform
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
    val nodeHierarchy: Tree<ModelNode> = TreeImpl(ModelNode(0,"", baseTransform = Transform()), listOf()),
    val skeleton: Skeleton? = null,
) {
    val baseRootTransform = nodeHierarchy.value.baseTransform.toMatrix()

    companion object;
}


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
        val scaledTb = other.translation.mul(scale, dst.translation)
        //    step B: rotate it by this rotation
        val rotatedScaledTb = rotation.rotate(scaledTb, dst.translation)      // assumes Quatf.times(Vec3f)
        //    step C: finally add this translation
       rotatedScaledTb.add(translation, dst.translation)
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


data class Animation(
    val name: String,
    /**
     * Sorted by time
     */
    val keyFrames: List<Pair<Duration, PartialSkeletonTransform>>,
)

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

class Material(val texture: FunImage? = null)