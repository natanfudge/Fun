package io.github.natanfudge.fn.hotreload

import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.ygdrasil.webgpu.examples.helper.glb.GLTF2
import io.ygdrasil.webgpu.examples.helper.glb.readGLB
import korlibs.image.format.PNG
import korlibs.image.format.encode
import korlibs.io.file.std.localVfs
import korlibs.memory.Buffer
import kotlinx.coroutines.runBlocking
import natan.`fun`.generated.resources.Res
import java.net.URI
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.io.path.toPath

fun main() {
    val model = Model.fromGlbResource("files/models/cube.glb")
}

fun Model.Companion.fromGlbResource(path: String): Model {
    val url = URI(Res.getUri(path)).toPath().toAbsolutePath()
    val glb = runBlocking { localVfs(url.toString()).readGLB() }

    val mesh = glb.meshes.singleOrNull() ?: error("Expected only one mesh in model at $url")

    // We'll use the first primitive from the mesh
    val primitive = mesh.primitives.firstOrNull() ?: error("No primitives found in mesh at $url")

    // Extract indices
    val indicesAccessorIndex = primitive.indices ?: error("No indices found in primitive at $url")
    val indicesAccessor = glb.accessors[indicesAccessorIndex]
    val indicesVector = indicesAccessor.accessor(glb)
//    val indicesBuffer = indicesAccessor.bufferSlice(glb)
//    indicesBuffer.buffer.order(ByteOrder.LITTLE_ENDIAN)

    val indicesArray = IntArray(indicesAccessor.count) { i ->
        indicesVector[i, 0].toInt()
//        when (indicesAccessor.componentType) {
//            0x1401 -> (indicesBuffer.getByteAt(i) and 0xFF.toByte()).toInt() // UNSIGNED_BYTE
//            0x1403 -> indicesBuffer.getShortAt(i * 2).toInt() and 0xFFFF // UNSIGNED_SHORT
//            0x1405 -> indicesBuffer.getIntAt(i * 4) // UNSIGNED_INT
//            else -> error("Unsupported index type: ${indicesAccessor.componentType}")
//        }
    }
    val indices = TriangleIndexArray(indicesArray)

    // Extract positions
    val positionAccessorIndex = primitive.attributes.entries.find { it.key.str == "POSITION" }?.value
        ?: error("No position attribute found in primitive at $url")
    val positionAccessor = glb.accessors[positionAccessorIndex]
    //TODO: use the vector, it's pog
    val positionBuffer = positionAccessor.bufferSlice(glb)
    val positions = List(positionAccessor.count) { i ->
        Vec3f(
            positionBuffer.getFloatAt(i * 12),
            positionBuffer.getFloatAt(i * 12 + 4),
            positionBuffer.getFloatAt(i * 12 + 8)
        )
    }

    // Extract normals (if available)
    val normalAccessorIndex = primitive.attributes.entries.find { it.key.str == "NORMAL" }?.value
    val normals = if (normalAccessorIndex != null) {
        val normalAccessor = glb.accessors[normalAccessorIndex]
        val normalBuffer = normalAccessor.bufferSlice(glb)
        List(normalAccessor.count) { i ->
            Vec3f(
                normalBuffer.getFloatAt(i * 12),
                normalBuffer.getFloatAt(i * 12 + 4),
                normalBuffer.getFloatAt(i * 12 + 8)
            )
        }
    } else {
        // If normals are not provided, we'll create a list of zero vectors
        // The Mesh.withNormals function will calculate proper normals
        List(positions.size) { Vec3f(0f, 0f, 0f) }
    }

    // Extract UVs (if available)
    val uvAccessorIndex = primitive.attributes.entries.find { it.key.str == "TEXCOORD_0" }?.value
    val uvs = if (uvAccessorIndex != null) {
        val uvAccessor = glb.accessors[uvAccessorIndex]
        val uvBuffer = uvAccessor.bufferSlice(glb)
        List(uvAccessor.count) { i ->
            UV(
                uvBuffer.getFloatAt(i * 8),
                uvBuffer.getFloatAt(i * 8 + 4)
            )
        }
    } else {
        // If UVs are not provided, we'll create a list of zero UVs
        List(positions.size) { UV(0f, 0f) }
    }

    // Create the mesh
    val resultMesh = if (normalAccessorIndex == null) {
        // If normals were not provided, use withNormals to calculate them
        Mesh.inferredNormals(indices, positions, uvs)
    } else {
        // If normals were provided, create the mesh directly
        val vertexBuffer = VertexArrayBuffer.of(positions, normals, uvs)
        Mesh(indices, vertexBuffer)
    }

    // Extract material and texture (if available)
    val material = primitive.material
    val texture = if (material != null) {
        null //TODo, wgpu copy issues
//        val gltfMaterial = glb.materials[material]
//        val baseColorTexture = gltfMaterial.pbrMetallicRoughness?.baseColorTexture
//        if (baseColorTexture != null) {
//            val textureIndex = baseColorTexture.index
//            val gltfTexture = glb.textures[textureIndex]
//            val imageIndex = gltfTexture.source
//            val gltfImage = glb.images[imageIndex]
//            gltfImage.toImage()
//        } else {
//            null
//        }
    } else {
        null
    }

//    val test = resultMesh.indices.forEachTriangle {  }

    // Create and return the model
    return Model(resultMesh, url.toFile().nameWithoutExtension, Material(texture))
}

private fun GLTF2.Image.toImage(): FunImage? {
    val bitmap = bitmap ?: return null
    val bytes = runBlocking { bitmap.encode(PNG) }

    return FunImage(
        width = bitmap.width,
        height = bitmap.height,
        bytes = bytes,
    )
}

// Extension functions for Buffer to make it easier to work with
private fun Buffer.getByteAt(index: Int): Byte = this.buffer.get(index)
private fun Buffer.getShortAt(index: Int): Short = this.buffer.getShort(index)
private fun Buffer.getIntAt(index: Int): Int = this.buffer.getInt(index)
private fun Buffer.getFloatAt(index: Int): Float = this.buffer.getFloat(index)
