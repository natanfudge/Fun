package io.github.natanfudge.fn.gltf

import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.render.*
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

fun main() {
    val model = Model.fromGlbResource("files/models/textured_cube.glb")
}

//TODO: bug: Seems like model is imported upside-down, but I IDK if it's a specific problem with the model I used
// It's actually because the glb file specifies a rotation of (0.7,0.7,0,0)!
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
    val positionVector = positionAccessor.accessor(glb)

    //TODO: use the vector, it's pog
    val positionBuffer = positionAccessor.bufferSlice(glb)
    val positions = List(positionAccessor.count) { i ->
        Vec3f(
            positionVector[i,0],
            positionVector[i,1],
            positionVector[i,2],
//            positionBuffer.getFloatAt(i * 12),
//            positionBuffer.getFloatAt(i * 12 + 4),
//            positionBuffer.getFloatAt(i * 12 + 8)
        )
    }

    // Extract normals (if available)
    val normalAccessorIndex = primitive.attributes.entries.find { it.key.str == "NORMAL" }?.value
//    val normalAccessorIndex = null
//    val normals = listOf<Vec3f>()
    val normals = if (normalAccessorIndex != null) {
        val normalAccessor = glb.accessors[normalAccessorIndex]
        val vector = normalAccessor.accessor(glb)

//        val normalBuffer = normalAccessor.bufferSlice(glb)
        List(normalAccessor.count) { i ->
            Vec3f(
                vector[i, 0],
                vector[i, 1],
                vector[i, 2]
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
        val view = glb.bufferViews[uvAccessor.bufferView]

        val uvStride = if (view.byteStride == 0) uvAccessor.bytesPerEntry else view.byteStride

//        println(">>> UV accessor debug")
//        println("componentType  = ${uvAccessor.componentType}  (should be 5126/FLOAT)")
//        println("ncomponent     = ${uvAccessor.ncomponent}     (should be 2)")
//        println("bytesPerEntry  = ${uvAccessor.bytesPerEntry}")
//        println("byteOffset     = ${uvAccessor.byteOffset}")
//        println("bufferView.stride = ${view.byteStride}  *** ← THIS is the killer ***")
//        println("calculated stride = $uvStride")
        require(uvStride == uvAccessor.bytesPerEntry) {
            "Interleaved vertex data detected: stride=$uvStride but entry=${uvAccessor.bytesPerEntry}"
        }

//        println("stride = ${view.byteStride}, bytesPerEntry = ${uvAccessor.bytesPerEntry}")

        check(uvAccessor.componentType == 5126)        // FLOAT
        check(uvAccessor.ncomponent   == 2)            // vec2
        check(uvAccessor.bytesPerEntry== 8)

        require(uvAccessor.count == positionAccessor.count) {
            "POSITION has ${positionAccessor.count} vertices but TEXCOORD_0 has ${uvAccessor.count}"
        }




        // raw 16 bytes starting from bufferView.byteOffset
        val rawStart = uvAccessor.bufferView(glb)
            .slice(glb)


// …and 16 bytes from the slice YOU actually passed to GLTF2AccessorVector
        val testSlice = uvAccessor.bufferSlice(glb)


//        println(">>>  bytes @ expected offset: ${rawStart.hex()}")
//        println(">>> bytes you gave vector : ${testSlice.hex()}")

        val vector = uvAccessor.accessor(glb)

        val byStride = uvAccessor.bufferView(glb).byteStride
        if (byStride != 0 && byStride != uvAccessor.bytesPerEntry) {
//            println("⚠︎  Interleaved attributes detected: stride=$byStride entry=${uvAccessor.bytesPerEntry}")
        }

        for (i in 0 until uvAccessor.count) {
            val u = vector[i,0]; val v = vector[i,1]
            check(u in 0f..1f && v in 0f..1f) { "UV[$i] out of range: ($u,$v)" }
        }

//        printFirstN(positionVector, vector, 24)


//        val uvBuffer = uvAccessor.bufferSlice(glb)
        List(uvAccessor.count) { i ->
            UV(  vector[i, 0],  vector[i,1])
        }
    } else {
        // If UVs are not provided, we'll create a list of zero UVs
        List(positions.size) { UV(0f, 0f) }
    }
//
//    println("======= TRIANGLE DUMP =======")
//    for (base in indicesArray.indices step 3) {
//        val tri      = base / 3
//        val i0       = indicesArray[base]
//        val i1       = indicesArray[base + 1]
//        val i2       = indicesArray[base + 2]
//
//        val p0 = positions[i0]; val uv0 = uvs[i0]
//        val p1 = positions[i1]; val uv1 = uvs[i1]
//        val p2 = positions[i2]; val uv2 = uvs[i2]
//
//        println(
//            "Tri $tri:\n" +
//                    "  v0: pos=$p0  uv=$uv0\n" +
//                    "  v1: pos=$p1  uv=$uv1\n" +
//                    "  v2: pos=$p2  uv=$uv2"
//        )
//    }
//    println("================================")
//
//    indicesArray.asList().chunked(3).take(4).forEachIndexed { tri, idx ->
//        println("Tri $tri:  ${idx.joinToString { i -> "UV${i}=${uvs[i]}, POS${i}=${positions[i]}" }}")
//    }

    if (positionAccessorIndex != null && uvAccessorIndex != null) {
        val posAccessor = glb.accessors[positionAccessorIndex]
        val uvAccessor = glb.accessors[uvAccessorIndex]
        val posVector = posAccessor.accessor(glb)
        val uvVector = uvAccessor.accessor(glb)
//        for (i in 0 until posAccessor.count) {
//            val pos = Vec3f(posVector[i, 0], posVector[i, 1], posVector[i, 2])
//            val uv = UV(uvVector[i, 0], uvVector[i, 1])
//            println("Vertex $i: pos=$pos, uv=$uv")
//        }

//        positionAccessor.debug("POSITION", glb)
//        uvAccessor.debug("TEXCOORD_0", glb)
//        indicesAccessor.debug("INDICES", glb)
    }

//    if (primitive.indices != null) {
//        val indicesAccessor = glb.accessors[primitive.indices!!]
//        val indicesVector = indicesAccessor.accessor(glb)
//        println("Indices: ${List(indicesAccessor.count) { indicesVector[it, 0] }}")
//    }


    // Create the mesh
    val resultMesh = if (normalAccessorIndex == null) {
        // If normals were not provided, use withNormals to calculate them
        Mesh.inferredNormals(indices, positions, uvs)
    } else {
        // If normals were provided, create the mesh directly
        val vertexBuffer = VertexArrayBuffer.of(positions, normals, uvs)
        Mesh(indices, vertexBuffer)
    }
// resultMesh.toTriangleList().find { (a, b, c) -> listOf(a,b,c).map { it.pos }.toSet() == setOf(Vec3f(-1f,1f,1f), Vec3f(1f,1f,1f), Vec3f(-1f,-1f,1f)) }
    // Extract material and texture (if available)
    val material = primitive.material
    val texture = if (material != null) {
//        null //TODo, wgpu copy issues
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

//    println(resultMesh)

//    val test = resultMesh.indices.forEachTriangle {  }

    // Create and return the model
    //TODO: placeholder texture to debug rendering issues
//    return Model(resultMesh, url.toFile().nameWithoutExtension, Material(texture = FunImage.fromResource("files/expected_texture.png")))
    return Model(resultMesh, url.toFile().nameWithoutExtension, Material(texture = texture))
}

fun printFirstN(positionVector: GLTF2AccessorVector,
                uvVector: GLTF2AccessorVector,
                n: Int = 24) {

    println("Idx |     Px      Py      Pz ||    U     V")
    println("----+------------------------++----------------")
    for (i in 0 until n) {
        val px = positionVector[i,0]
        val py = positionVector[i,1]
        val pz = positionVector[i,2]

        val u  = uvVector[i,0]
        val v  = uvVector[i,1]

        println("%3d | %7.3f %7.3f %7.3f || %6.3f %6.3f"
            .format(i, px, py, pz, u, v))
    }
}

fun GLTF2.Accessor.debug(tag: String, gltf: GLTF2) {
    val view = bufferView(gltf)
    println("[$tag]")
    println("  componentType   = $componentType")
    println("  type (ncomp)    = $type ($ncomponent)")
    println("  count           = $count")
    println("  byteOffset      = $byteOffset   (inside bufferView)")
    println("  bytesPerEntry   = $bytesPerEntry")
    println("  bufferView      = ${bufferView}")
    println("    • byteOffset  = ${view.byteOffset}  (into buffer)")
    println("    • byteLength  = ${view.byteLength}")
    println("    • byteStride  = ${view.byteStride}")
    println("--------------------------------------------------")
}

//fun BufferedImage.toFunImage(): FunImage? {
//
//}

private fun IntArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(this.size * 4)
    buffer.asIntBuffer().put(this)
    return buffer.array()
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

//TODO: use the cube example to see what is going on.. Also there seems to be some fighting issue

private fun GLTF2.Image.toImage(): FunImage? {

    val bitmap = bitmap ?: return null
    return (bitmap as AwtNativeImage).awtImage.toFunImage()
//    val bytes = runBlocking {
//        (bitmap as AwtNativeImage).awtData.toByteArray()
//    }

//    val bytes = runBlocking {
//        (bitmap as AwtNativeImage).awtImage.toFunImage()
//    }
//
//    return FunImage(
//        width = bitmap.width,
//        height = bitmap.height,
//        bytes = bytes,
//    )
}

// Extension functions for Buffer to make it easier to work with
private fun Buffer.getByteAt(index: Int): Byte = this.buffer.get(index)
private fun Buffer.getShortAt(index: Int): Short = this.buffer.getShort(index)
private fun Buffer.getIntAt(index: Int): Int = this.buffer.getInt(index)
private fun Buffer.getFloatAt(index: Int): Float = this.buffer.getFloat(index)
