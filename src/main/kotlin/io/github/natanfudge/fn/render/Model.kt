package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.util.Tree
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.time.Duration

data class Model(
    val mesh: Mesh,
    val id: ModelId,
    val material: Material = Material(),
    val initialTransform: Transform = Transform(),
    val animations: List<Animation> = listOf(),
    val skeleton: Skeleton? = null,
) {
    companion object;
}

data class Transform(
    val translation: Vec3f = Vec3f.zero(),
    val rotation: Quatf = Quatf.identity(),
    var scale: Vec3f = Vec3f(1f, 1f, 1f),
) {
    fun toMatrix() = Mat4f.translateRotateScale(translation, rotation, scale)
}


data class Animation(
    val name: String,
    val keyFrames: List<Pair<Duration, SkeletalTransformation>>
)

data class Joint(
    /**
     * An index associated with any animatable thing, such as a joint or the mesh itself.
     */
    val nodeIndex: Int,
    val baseTransform: Mat4f,
)


data class Skeleton(
    val joints: List<Joint>,
    val inverseBindMatrices: List<Mat4f>,
    /** References nodes by nodeIndex.  **/
    val hierarchy: Tree<Int>,
)



typealias ModelId = String

class Material(val texture: FunImage? = null)