package io.github.natanfudge.fn.gltf

import io.github.natanfudge.fn.compose.utils.TreeImpl
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.util.Tree
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.examples.helper.glb.GLTF2
import io.ygdrasil.webgpu.examples.helper.glb.readGLB
import korlibs.image.awt.AwtNativeImage
import korlibs.io.file.std.localVfs
import korlibs.time.seconds
import kotlinx.coroutines.runBlocking
import natan.`fun`.generated.resources.Res
import java.awt.image.BufferedImage
import java.net.URI
import kotlin.io.path.toPath


//SUS: gonna do this more robust and not global state when we have proper async model loading.
private val modelCache = mutableMapOf<String, Model>()

fun clearModelCache() = modelCache.clear()

fun Model.Companion.fromGlbResource(path: String): Model = modelCache.getOrPut(path) {
    Model.fromGlbResourceImpl(path)
}



private fun Model.Companion.fromGlbResourceImpl(path: String): Model {
    val url = URI(Res.getUri(path)).toPath().toAbsolutePath()
    val glb = runBlocking { localVfs(url.toString()).readGLB() }

    val mesh = glb.meshes.singleOrNull() ?: error("Expected exactly one mesh in model at $url (actual: ${glb.meshes.size})!")

    // We'll use the first primitive from the mesh
    val primitive = mesh.primitives.firstOrNull() ?: error("No primitives found in mesh at $url")

    // Extract indices
    val indicesAccessorIndex = primitive.indices ?: error("No indices found in primitive at $url")
    val indicesAccessor = glb.accessors[indicesAccessorIndex]
    val indicesVector = indicesAccessor.accessor(glb)

    val indicesArray = IntArray(indicesAccessor.count) { i ->
        indicesVector[i, 0].toInt()
    }

    val indices = TriangleIndexArray(indicesArray)

    // Extract positions
    val positionAccessorIndex = primitive.attributes.entries.find { it.key.str == "POSITION" }?.value
        ?: error("No position attribute found in primitive at $url")
    val positionAccessor = glb.accessors[positionAccessorIndex]
    val positionVector = positionAccessor.accessor(glb)

    val positions = List(positionAccessor.count) { i ->
        Vec3f(
            positionVector[i, 0],
            positionVector[i, 1],
            positionVector[i, 2],
        )
    }

    // Extract normals (if available)
    val normalAccessorIndex = primitive.attributes.entries.find { it.key.str == "NORMAL" }?.value
    val normals = if (normalAccessorIndex != null) {
        val normalAccessor = glb.accessors[normalAccessorIndex]
        val vector = normalAccessor.accessor(glb)

        List(normalAccessor.count) { i ->
            Vec3f(
                vector[i, 0],
                vector[i, 1],
                vector[i, 2]
            )
        }
    } else {
        Mesh.inferNormals(indices, positions)
    }


    // Extract UVs (if available)
    val uvAccessorIndex = primitive.attributes.entries.find { it.key.str == "TEXCOORD_0" }?.value


    val uvs = if (uvAccessorIndex != null) {
        val uvAccessor = glb.accessors[uvAccessorIndex]
        val vector = uvAccessor.accessor(glb)

        List(uvAccessor.count) { i ->
            UV(vector[i, 0], vector[i, 1])
        }
    } else {
        // If UVs are not provided, we'll create a list of zero UVs
        List(positions.size) { UV(0f, 0f) }
    }

    val jointsAccessorIndex = primitive.attributes.entries.find { it.key.str == "JOINTS_0" }?.value

    val joints: List<VertexJoints> = if (jointsAccessorIndex != null) {
        val accessor = glb.accessors[jointsAccessorIndex]
        val vector = accessor.accessor(glb)
        List(accessor.count) {
            VertexJoints(
                vector[it, 0].toInt(),
                vector[it, 1].toInt(),
                vector[it, 2].toInt(),
                vector[it, 3].toInt(),
            )
        }
    } else {
        listOf() // SLOW, should exclude joints/weights from the model object entirely
    }

    val weightsAccessorIndex = primitive.attributes.entries.find { it.key.str == "WEIGHTS_0" }?.value

    check((weightsAccessorIndex == null && jointsAccessorIndex == null) || (weightsAccessorIndex != null && jointsAccessorIndex != null)) {
        "Joints must exist together with weights - either both of them or none of them must be present"
    }

    val weights: List<VertexWeights> = if (weightsAccessorIndex != null) {
        val accessor = glb.accessors[weightsAccessorIndex]
        val vector = accessor.accessor(glb)
        List(accessor.count) {
            VertexWeights(
                vector[it, 0],
                vector[it, 1],
                vector[it, 2],
                vector[it, 3],
            )
        }
    } else {
        listOf()
    }


    // Create the mesh
    val vertexBuffer = VertexArrayBuffer.of(positions, normals, uvs, joints, weights)
    val resultMesh = Mesh(indices, vertexBuffer)

    // Extract material and texture (if available)
    val material = primitive.material
    val texture = if (material != null) {
        val gltfMaterial = glb.materials[material]
        val baseColorTexture = gltfMaterial.pbrMetallicRoughness?.baseColorTexture
        if (baseColorTexture != null) {
            val textureIndex = baseColorTexture.index
            val gltfTexture = glb.textures[textureIndex]
            val imageIndex = gltfTexture.source
            val gltfImage = glb.images[imageIndex]
            gltfImage.toImage(path)
        } else {
            null
        }
    } else {
        null
    }

    // Extract skeleton/joint information from skins
    val skeleton = extractSkeleton(glb)
    val animations = extractAnimations(glb)
    val nodeHierarchy = buildNodeHierarchy(glb)
    return Model(
        mesh = resultMesh,
        id = url.toFile().nameWithoutExtension,
        material = Material(texture = texture),
        animations = animations,
        nodeHierarchy = nodeHierarchy,
        skeleton = skeleton
    )
}

/**
 * Temporary object we use to gather around the transform parts specified in a gltf animation.
 * In the end they are all combined to a [Transform] matrix and just before rendering they become a matrix after interpolation.
 */
private data class TemporaryGltfTransformations(
    var translation: Vec3f? = null,
    var rotation: Quatf? = null,
    var scale: Vec3f? = null,
) {
    fun build() = PartialTransform(
        translation, rotation, scale
    )
}

data class PartialTransform(
    val translation: Vec3f?,
    val rotation: Quatf?,
    val scale: Vec3f?,
)

private fun extractAnimations(glb: GLTF2): List<Animation> {
    return glb.animations.map { animation ->
        val name = animation.name ?: "animation_${glb.animations.indexOf(animation)}"

        // Group channels by time, as a single keyframe can have multiple transformations (translation, rotation, scale)
        val keyframesByTime = mutableMapOf<Float, MutableMap<Int, TemporaryGltfTransformations>>()

        animation.channels.forEach { channel ->
            val sampler = animation.samplers[channel.sampler]
            val inputAccessor = glb.accessors[sampler.input]
            val outputAccessor = glb.accessors[sampler.output]
            val times = inputAccessor.accessor(glb)
            val values = outputAccessor.accessor(glb)
            val target = channel.target ?: return@forEach
            val targetNode = target.node

            for (i in 0 until inputAccessor.count) {
                val time = times[i, 0]
                val jointTransforms = keyframesByTime.getOrPut(time) { mutableMapOf() }
                val transform = jointTransforms.getOrPut(targetNode) { TemporaryGltfTransformations() }

                when (target.path) {
                    GLTF2.Animation.Channel.TargetPath.TRANSLATION -> {
                        transform.translation = Vec3f(values[i, 0], values[i, 1], values[i, 2])
                    }

                    GLTF2.Animation.Channel.TargetPath.ROTATION -> {
                        transform.rotation = Quatf(values[i, 0], values[i, 1], values[i, 2], values[i, 3])
                    }

                    GLTF2.Animation.Channel.TargetPath.SCALE -> {
                        transform.scale = Vec3f(values[i, 0], values[i, 1], values[i, 2])
                    }

                    else -> {
                        // Unsupported path
                    }
                }
            }
        }

        val keyFrames = keyframesByTime.entries.map { (time, transforms) ->
            KeyFrame(time.seconds, transforms.mapValues { (_, value) -> value.build() })
        }.sortedBy { it.time }

        Animation(name, keyFrames)
    }
}


/**
 * Converts this [BufferedImage] to a [FunImage].
 *
 * - The returned byte array is laid out in **interleaved** component order:
 *   `R, G, B` (or `R, G, B, A` when the source has alpha).
 * - Components are 8-bit sRGB values, ready for formats such as `RGBA8_SRGB`
 *   when uploading to a GPU.
 * - Returns `null` for empty images.
 */
fun BufferedImage.toFunImage(path: String): FunImage? {
    if (width == 0 || height == 0) return null

    val hasAlpha = colorModel.hasAlpha()
    val compsPerPixel = if (hasAlpha) 4 else 3
    val out = ByteArray(width * height * compsPerPixel)

    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = getRGB(x, y)          // always delivered in default sRGB
            val r = (argb ushr 16) and 0xFF  // red
            val g = (argb ushr 8) and 0xFF  // green
            val b = argb and 0xFF  // blue
            out[i++] = r.toByte()
            out[i++] = g.toByte()
            out[i++] = b.toByte()
            if (hasAlpha) {
                val a = (argb ushr 24) and 0xFF
                out[i++] = a.toByte()
            }
        }
    }

    return FunImage(width, height, out, path)
}


private fun GLTF2.Image.toImage(path: String): FunImage? {
    val bitmap = bitmap ?: return null
    return (bitmap as AwtNativeImage).awtImage.toFunImage(path)
}

/**
 * Extracts transformation data from the GLTF2 model.
 * Looks for the first node that has a mesh and extracts its transformation data.
 */
private fun GLTF2.Node.extractTransform(): Transform {
    if (matrix != null) error("No matrix import support yet")
    val nodeTranslation = translation
    val nodeRotation = rotation
    val nodeScale = scale

    // Extract position (translation)
    val position = if (nodeTranslation != null && nodeTranslation.size >= 3) {
        Vec3f(nodeTranslation[0], nodeTranslation[1], nodeTranslation[2])
    } else {
        Vec3f.zero()
    }

    // Extract rotation (quaternion)
    val rotation = (if (nodeRotation != null && nodeRotation.size >= 4) {
        Quatf(nodeRotation[0], nodeRotation[1], nodeRotation[2], nodeRotation[3])
    } else {
        Quatf.identity()
    })

    // Extract scale
    val scale = if (nodeScale != null && nodeScale.size >= 3) {
        Vec3f(nodeScale[0], nodeScale[1], nodeScale[2])
    } else {
        Vec3f(1f, 1f, 1f)
    }

    return Transform(position, rotation, scale)
}

/**
 * Extracts skeleton/joint information from the GLTF2 model.
 * Looks for skins in the model and extracts joint indices and inverse bind matrices.
 */
private fun extractSkeleton(glb: GLTF2): Skeleton? {
    // Check if the model has any skins
    val skins = glb.skins
    if (skins.isEmpty()) {
        return null
    }

    // Use the first skin (most models have only one skin)
    val skin = skins.first()

    // Extract joint information
//    val jointIndices = skin.joints.toList()
//    val joints = jointIndices.map { jointIndex ->
//        // Get the node for this joint to extract its base transform
//        val node = glb.nodes[jointIndex]
//        val matrix = node.matrix
//        val baseTransform = if (matrix != null && matrix.size >= 16) {
//            Transform.fromMatrix(Mat4f(matrix))
//        } else {
//            node.extractTransform()
//        }
//
//        Joint(jointIndex, baseTransform)
//    }

    // Extract inverse bind matrices
    val inverseBindMatrices = if (skin.inverseBindMatrices != null) {
        val accessorIndex = skin.inverseBindMatrices!!
        val accessor = glb.accessors[accessorIndex]
        val vector = accessor.accessor(glb)
        List(accessor.count) { i ->
            Mat4f(
                vector[i, 0], vector[i, 1], vector[i, 2], vector[i, 3],
                vector[i, 4], vector[i, 5], vector[i, 6], vector[i, 7],
                vector[i, 8], vector[i, 9], vector[i, 10], vector[i, 11],
                vector[i, 12], vector[i, 13], vector[i, 14], vector[i, 15]
            )
        }
    } else {
        // If no inverse bind matrices are provided, use identity matrices
        List(skin.joints.size) { Mat4f.identity() }
    }

    // Build the joint hierarchy tree
//    val hierarchy = buildJointHierarchy(glb, jointIndices)

    return Skeleton(skin.joints.toList(), inverseBindMatrices)
}

private fun buildNodeHierarchy(glb: GLTF2): Tree<ModelNode> {
    val scene = glb.scenes[glb.scene]
    val rootNodeIndices = scene.nodes

    val tree = rootNodeIndices.map { nodeIndex ->
        buildNodeTreeRecursive(glb, nodeIndex)
    }


    return if (tree.size == 1) {
        tree.single()
    } else {
        error("We do not support multiple root nodes yet")
        // If there is only one root node, we can use it directly.
        // Otherwise, we create a virtual root node to hold all root nodes from the scene.
//        val virtualRootNode = ModelNode(id = -1, baseTransform = Transform())
//        TreeImpl(virtualRootNode, children)
    }
}

private fun buildNodeTreeRecursive(glb: GLTF2, nodeIndex: Int): Tree<ModelNode> {
    val node = glb.nodes[nodeIndex]
    val modelNode = ModelNode(
        id = nodeIndex,
        name = node.name ?: "node_$nodeIndex",
        baseTransform = node.extractTransform()
    )
    val children = node.children.map { childIndex ->
        buildNodeTreeRecursive(glb, childIndex)
    }

    return TreeImpl(modelNode, children)
}
//
///**
// * Builds a joint hierarchy tree from GLTF node structure.
// * Returns a Tree<Int> where each node contains a joint's nodeIndex.
// */
//private fun buildJointHierarchy(glb: GLTF2, jointIndices: List<Int>): Tree<Int> {
//    val jointIndexSet = jointIndices.toSet()
//
//    // A root joint is a joint that is not a child of any other joint in the same skin.
//    // We find all children of all joints in the skeleton.
//    val childJointIndices = jointIndices
//        .flatMap { jointIndex -> glb.nodes[jointIndex].children.toList() }
//        .toSet()
//
//    // The root joints are the ones that are not in the set of children.
//    val rootJoints = jointIndices.filter { it !in childJointIndices }
//
//    // A valid skeleton should have a single root. If we find more than one,
//    // the skeleton is disjointed. If we find none, there might be a cycle.
//    // We'll assume a single root for now.
//    val rootNodeIndex = rootJoints.singleOrNull()
//        ?: error("Could not determine a single root joint for the skeleton. Found roots: $rootJoints.")
//
//    return buildTreeRecursive(glb, jointIndexSet, rootNodeIndex)
//}

//// Build the tree recursively starting from the root.
//// The tree should only contain nodes that are part of this skeleton's joints.
//fun buildTreeRecursive(glb: GLTF2, jointIndexSet: Set<Int>, nodeIndex: Int): Tree<Int> {
//    val node = glb.nodes[nodeIndex]
//    val children = node.children
//        .filter { it in jointIndexSet } // Only include children that are also joints of this skin
//        .map { childIndex -> buildTreeRecursive(glb, jointIndexSet, childIndex) }
//
//    return TreeImpl(nodeIndex, children)
//}