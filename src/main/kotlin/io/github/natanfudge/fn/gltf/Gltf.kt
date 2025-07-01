package io.github.natanfudge.fn.gltf

import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.examples.helper.glb.GLTF2
import io.ygdrasil.webgpu.examples.helper.glb.GLTF2AccessorVector
import io.ygdrasil.webgpu.examples.helper.glb.readGLB
import korlibs.image.awt.AwtNativeImage
import korlibs.io.file.std.localVfs
import korlibs.memory.Buffer
import korlibs.memory.hex
import kotlinx.coroutines.runBlocking
import natan.`fun`.generated.resources.Res
import java.awt.image.BufferedImage
import java.net.URI
import java.nio.ByteBuffer
import kotlin.io.path.toPath
import kotlin.math.PI


//SUS: gonna do this more robust and not global state when we have proper async model loading. 
private val modelCache = mutableMapOf<String, Model>()

fun clearModelCache() = modelCache.clear()

fun Model.Companion.fromGlbResource(path: String): Model = modelCache.getOrPut(path) {
    Model.fromGlbResourceImpl(path)
}

//TODO: last thing - change vba definition in webgpu code, then see if everything works the same way.

fun Model.Companion.fromGlbResourceImpl(path: String): Model {
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
            positionVector[i,0],
            positionVector[i,1],
            positionVector[i,2],
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
            UV(  vector[i, 0],  vector[i,1])
        }
    } else {
        // If UVs are not provided, we'll create a list of zero UVs
        List(positions.size) { UV(0f, 0f) }
    }
    // Create the mesh
    val vertexBuffer = VertexArrayBuffer.of(positions, normals, uvs, listOf(), listOf()) //TODO: joints/weights
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
            gltfImage.toImage()
        } else {
            null
        }
    } else {
        null
    }

    // Extract transformation data from the node
    val transform = extractTransform(glb)

    return Model(resultMesh, url.toFile().nameWithoutExtension, Material(texture = texture), transform)
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
fun BufferedImage.toFunImage(): FunImage? {
    if (width == 0 || height == 0) return null

    val hasAlpha = colorModel.hasAlpha()
    val compsPerPixel = if (hasAlpha) 4 else 3
    val out = ByteArray(width * height * compsPerPixel)

    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = getRGB(x, y)          // always delivered in default sRGB
            val r = (argb ushr 16) and 0xFF  // red
            val g = (argb ushr 8)  and 0xFF  // green
            val b =  argb         and 0xFF  // blue
            out[i++] = r.toByte()
            out[i++] = g.toByte()
            out[i++] = b.toByte()
            if (hasAlpha) {
                val a = (argb ushr 24) and 0xFF
                out[i++] = a.toByte()
            }
        }
    }

    return FunImage(width, height, out)
}


private fun GLTF2.Image.toImage(): FunImage? {
    val bitmap = bitmap ?: return null
    return (bitmap as AwtNativeImage).awtImage.toFunImage()
}

/**
 * Extracts transformation data from the GLTF2 model.
 * Looks for the first node that has a mesh and extracts its transformation data.
 */
private fun extractTransform(glb: GLTF2): Transform {
    // Find the first node that has a mesh
    val node = glb.nodes.firstOrNull { it.mesh != null }

    if (node == null) return Transform() // Default transform if no node with mesh is found

    // Extract position (translation)
    val position = if (node.translation != null && node.translation!!.size >= 3) {
        Vec3f(node.translation!![0], node.translation!![1], node.translation!![2])
    } else {
        Vec3f.zero()
    }

    // Extract rotation (quaternion)
    val rotation = (if (node.rotation != null && node.rotation!!.size >= 4) {
        Quatf(node.rotation!![0], node.rotation!![1], node.rotation!![2], node.rotation!![3])
    } else {
        Quatf.identity()
        //TODO: this rotateX is kinda confusing
    })/*.rotateX(PI.toFloat() / 2)*/ // GLTF uses Y-up, rotating the X axis by 90 degrees makes it match Z-up instead.

    // Extract scale
    val scale = if (node.scale != null && node.scale!!.size >= 3) {
        Vec3f(node.scale!![0], node.scale!![1], node.scale!![2])
    } else {
        Vec3f(1f, 1f, 1f)
    }

    return Transform(position, rotation, scale)
}
