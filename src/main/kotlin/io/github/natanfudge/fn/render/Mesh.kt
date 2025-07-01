package io.github.natanfudge.fn.render

import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

typealias Point3f = Vec3f

enum class CubeUv {
    /**
     * Repeat the same textured across all sides
     */
    Repeat,

    /**
     * Draw the textures for the sides from a 3x2 grid
     */
    Grid3x2
}

private val verifyMeshes = true

class Mesh(val indices: TriangleIndexArray, val vertices: VertexArrayBuffer) {
    init {
        if (verifyMeshes) {
            val visitedIndices = BooleanArray(vertices.size) { false }
            indices.forEachTriangle { a, b, c ->
                check(a < vertices.size) { "Triangle index $a is out of bounds of vertex array of size ${vertices.size}" }
                check(b < vertices.size) { "Triangle index $b is out of bounds of vertex array of size ${vertices.size}" }
                check(c < vertices.size) { "Triangle index $c is out of bounds of vertex array of size ${vertices.size}" }
                visitedIndices[a] = true
                visitedIndices[b] = true
                visitedIndices[c] = true
            }
            val unvisitedIndex = visitedIndices.indexOfFirst { !it }
            check(unvisitedIndex == -1) { "Mesh contains unvisited index $unvisitedIndex" }
        }
    }

    companion object {
        /**
         * Automatically infers normals for all vertices.
         * Note that the "smoothness" of the shading depends on how much the indices are shared between vertices.
         * If all indices use separate vertices, the shading will be flat.
         * If vertices share indices, the normal will be average out for that index, making the shading smooth.
         */
        fun inferNormals(
            indices: TriangleIndexArray,
            positions: List<Point3f>,
        ): List<Vec3f> {
            // one *distinct* accumulator per vertex
            val normals = MutableList(positions.size) { Vec3f() }

            // accumulate face normals
            indices.forEachTriangle { a, b, c ->
                val n = inferNormal(positions[a], positions[b], positions[c])
                normals[a] += n
                normals[b] += n
                normals[c] += n
            }

            // normalise once, after accumulation
            for (i in normals.indices) {
                normals[i].normalize(normals[i])
            }
            return normals
        }


        val HomogenousCube = unitCube(CubeUv.Repeat)
        val HeterogeneousCube = unitCube(CubeUv.Grid3x2)


        // There is actually only 24 unique positions, we don't need 36
        /**
         * Creates a cube that partially shares vertices, in a way that has the minimum amount of vertices, but allows for correct flat shading.
         */
        private fun unitCube(uv: CubeUv = CubeUv.Repeat): Mesh {
            val positions = listOf(
                // top (Z = 1)
                Vec3f(-0.5f, -0.5f, 0.5f), Vec3f(0.5f, -0.5f, 0.5f), Vec3f(0.5f, 0.5f, 0.5f), Vec3f(-0.5f, 0.5f, 0.5f),
                // bottom (Z = 0)
                Vec3f(0.5f, -0.5f, -0.5f), Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(-0.5f, 0.5f, -0.5f), Vec3f(0.5f, 0.5f, -0.5f),
                // left (X = 0)
                Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(-0.5f, -0.5f, 0.5f), Vec3f(-0.5f, 0.5f, 0.5f), Vec3f(-0.5f, 0.5f, -0.5f),
                // right (X = 1)
                Vec3f(0.5f, -0.5f, 0.5f), Vec3f(0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f),
                // front (Y = 1)
                Vec3f(-0.5f, 0.5f, 0.5f), Vec3f(0.5f, 0.5f, 0.5f), Vec3f(0.5f, 0.5f, -0.5f), Vec3f(-0.5f, 0.5f, -0.5f),
                // back (Y = 0)
                Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, -0.5f, -0.5f), Vec3f(0.5f, -0.5f, 0.5f), Vec3f(-0.5f, -0.5f, 0.5f)
            )
            val indices = TriangleIndexArray.of(
                0, 1, 2, 0, 2, 3,      // front
                4, 5, 6, 4, 6, 7,      // back
                8, 9, 10, 8, 10, 11,      // left
                12, 13, 14, 12, 14, 15,      // right
                16, 17, 18, 16, 18, 19,      // top
                20, 21, 22, 20, 22, 23       // bottom
            )
            val normals = inferNormals(indices, positions)
            val vba = VertexArrayBuffer.of(
                positions, normals,
                uv = when (uv) {
                    CubeUv.Repeat -> listOf(
                        // top (Z = 1)
                        UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                        // bottom (Z = 0)
                        UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                        // left (X = 0)
                        UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                        // right (X = 1)
                        UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                        // front (Y = 1)
                        UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                        // back (Y = 0)
                        UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f)
                    )

                    CubeUv.Grid3x2 -> listOf(


                        // Top
                        UV(0f, 0.5f), UV(1f / 3f, 0.5f), UV(1f / 3f, 0f), UV(0f, 0f),
                        // Bottom
                        UV(2f / 3f, 1f), UV(1f, 1f), UV(1f, 0.5f), UV(2f / 3f, 0.5f),
                        // Left
                        UV(0f, 1f), UV(1f / 3f, 1f), UV(1f / 3f, 0.5f), UV(0f, 0.5f),
                        // Right
                        UV(1f / 3f, 1f), UV(2f / 3f, 1f), UV(2f / 3f, 0.5f), UV(1f / 3f, 0.5f),
                        // Front
                        UV(1f / 3f, 0.5f), UV(2f / 3f, 0.5f), UV(2f / 3f, 0f), UV(1f / 3f, 0f),
                        // Back
                        UV(2f / 3f, 0.5f), UV(1f, 0.5f), UV(1f, 0f), UV(2f / 3f, 0f)
                    )
                },
                jointList = listOf(), //SLOW: should not need this
                weightList = listOf()
            )
            return Mesh(indices, vba)
        }


        /**
         * Creates a sphere that shares vertices as much as possible, making shading smooth.
         */
        fun uvSphere(segments: Int = 64): Mesh {
            val vertices = mutableListOf<Point3f>()
            val indices = mutableListOf<Int>()

            // Generate vertices
            for (i in 0..segments) {
                val phi = PI * i / segments
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)

                for (j in 0 until segments) {
                    val theta = 2 * PI * j / segments
                    val sinTheta = sin(theta)
                    val cosTheta = cos(theta)

                    // Convert spherical coordinates to Cartesian
                    val x = sinPhi * cosTheta
                    val y = sinPhi * sinTheta
                    val z = cosPhi

                    vertices.add(Point3f(x.toFloat(), y.toFloat(), z.toFloat()))
                }
            }

            val ringStride = segments        // vertices per latitude ring

            for (i in 0 until segments) {    // latitude    (0 .. segments-1)
                for (j in 0 until segments) { // longitude  (0 .. segments-1)
                    val nextJ = (j + 1) % segments   // wrap around at the seam

                    val first = i * ringStride + j
                    val second = i * ringStride + nextJ
                    val third = (i + 1) * ringStride + j
                    val fourth = (i + 1) * ringStride + nextJ

                    // first triangle
                    indices += first
                    indices += third
                    indices += second

                    // second triangle
                    indices += second
                    indices += third
                    indices += fourth
                }
            }

            val indexArray = TriangleIndexArray(indices.toIntArray())

            return Mesh(
                indices = indexArray,
                vertices = VertexArrayBuffer.of(
                    vertices,
                    normals = inferNormals(indexArray, vertices),
                    uv = buildList {
                        for (i in 0..segments) {
                            val v = i.toFloat() / segments

                            for (j in 0 until segments) {
                                val u = j.toFloat() / segments
                                add(UV(u, v))
                            }
                        }
                    },
                    jointList = listOf(), // slow: should not need to insert anything here, just disable joints / weights
                    weightList = listOf()
                )
            )
        }
    }

    val triangles get() = vertices.size / VertexArrayBuffer.StrideFloats
    val vertexCount = vertices.size.toULong()
    val indexCount = indices.size.toUInt()
    val verticesByteSize get() = vertices.byteSize

    override fun toString(): String {
        return toTriangleList().toString()
    }

    fun toTriangleList(): List<ModelTriangle> = buildList {
        forEachTriangle { a, b, c ->
            add(ModelTriangle(a, b, c))
        }
    }

    inline fun forEachTriangle(iter: (a: Vertex, b: Vertex, c: Vertex) -> Unit) {
        indices.forEachTriangle { a, b, c ->
            iter(
                vertices[a], vertices[b], vertices[c]
            )
        }
    }

    inline fun forEachVertexByOrder(iter: (v: Vertex) -> Unit) {
        indices.forEach {
            iter(vertices[it])
        }
    }

    inline fun forEachVertex(iter: (v: Vertex) -> Unit) {
        vertices.forEachVertex(iter)
    }

    inline fun forEachPos(iter: (pos: Point3f) -> Unit) {
        vertices.forEachPosition(iter)
    }

}


data class ModelTriangle(val a: Vertex, val b: Vertex, val c: Vertex) {
    override fun toString(): String {
        return "<$a,$b,$c>"
    }
}

class Vertex(val pos: Point3f, val normal: Vec3f, val uv: UV, val joints: VertexJoints, val weights: VertexWeights) {
    override fun toString(): String {
        return "pos=$pos,norm=$normal,uv=$uv"
    }
}

/**
 * Determine the direction of a face of a mesh (a  triangle [a], [b], [c])
 * Assumes the triangle is in counter-clockwise order, otherwise this vector will incorrectly point inwards instead of outwards.
 */
private fun inferNormal(a: Point3f, b: Point3f, c: Point3f): Vec3f {
    return (b - a).cross(c - a).normalize()
}


data class UV(val u: Float, val v: Float) {
    override fun toString(): String {
        return "($u,$v)"
    }
}

data class VertexJoints(
    val a: Int, val b: Int, val c: Int, val d: Int
)

data class VertexWeights(
    val a: Float, val b: Float, val c: Float, val d: Float
)


class VertexArrayBuffer(val array: FloatArray) {
    companion object {
        //SLOW: should have a way to turn on/off attributes of the vertex
        const val StrideFloats = 16
        val StrideBytes = (StrideFloats * Float.SIZE_BYTES).toULong()

        //        fun of(vararg floats: Float) = VertexArrayBuffer(floats)
        fun of(
            positions: List<Point3f>,
            normals: List<Vec3f>,
            uv: List<UV>,
            jointList: List<VertexJoints>,
            weightList: List<VertexWeights>,
        ): VertexArrayBuffer {
            require(normals.size == positions.size)
            require(uv.size == positions.size) { "The amount of UVS (${uv.size}) doesn't match the amount of positions (${positions.size})" }
            val array = FloatArray(positions.size * StrideFloats)
            positions.forEachIndexed { i, pos ->
                array[i * StrideFloats] = pos.x
                array[i * StrideFloats + 1] = pos.y
                array[i * StrideFloats + 2] = pos.z
            }

            normals.forEachIndexed { i, vec ->
                array[i * StrideFloats + 3] = vec.x
                array[i * StrideFloats + 4] = vec.y
                array[i * StrideFloats + 5] = vec.z
            }
            uv.forEachIndexed { i, (u, v) ->
                array[i * StrideFloats + 6] = u
                array[i * StrideFloats + 7] = v
            }
            jointList.forEachIndexed { i, joints ->
                array[i * StrideFloats + 8] = joints.a.toFloat() //SLOW: should pass in as an int but that's annoying, passing as float and converting for now.
                array[i * StrideFloats + 9] = joints.b.toFloat()
                array[i * StrideFloats + 10] = joints.c.toFloat()
                array[i * StrideFloats + 11] = joints.d.toFloat()
            }
            weightList.forEachIndexed { i, weights ->
                array[i * StrideFloats + 12] = weights.a
                array[i * StrideFloats + 13] = weights.b
                array[i * StrideFloats + 14] = weights.c
                array[i * StrideFloats + 15] = weights.d
            }
            return VertexArrayBuffer(array)
        }
    }

//    constructor(points: List<Point3f>) : this(
//        FloatArray(points.size * 3).also { arr ->
//            var i = 0
//            for (point in points) {
//                arr[i++] = point.x
//                arr[i++] = point.y
//                arr[i++] = point.z
//            }
//        }
//    )

    val size get() = array.size / StrideFloats
    val byteSize get() = (array.size * Float.SIZE_BYTES).toULong()

    operator fun get(index: Int): Vertex {
        var i = index * StrideFloats
        return Vertex(
            Point3f(array[i++], array[i++], array[i++]),
            Vec3f(array[i++], array[i++], array[i++]),
            UV(array[i++], array[i++]),
            VertexJoints(array[i++].toInt(), array[i++].toInt(), array[i++].toInt(), array[i++].toInt()),
            VertexWeights(array[i++], array[i++], array[i++], array[i++])
        )
    }

    override fun toString(): String {
        return toList().toString()
    }

    fun toList(): List<Vertex> = buildList {
        forEachVertex { v ->
            add(v)
        }
    }

    inline fun forEachVertex(iter: (v: Vertex) -> Unit) {
        var i = 0
        while (i < array.size) {
            iter(
                Vertex(
                    Point3f(array[i++], array[i++], array[i++]),
                    Vec3f(array[i++], array[i++], array[i++]),
                    UV(array[i++], array[i++]),
                    VertexJoints(array[i++].toInt(), array[i++].toInt(), array[i++].toInt(), array[i++].toInt()),
                    VertexWeights(array[i++], array[i++], array[i++], array[i++])
                )
            )
        }
    }

    inline fun forEachPosition(iter: (p: Point3f) -> Unit) {
        var i = 0
        while (i < array.size) {
            iter(
                Point3f(array[i++], array[i++], array[i++])
            )
            i += 5 // Skip normal and uv
        }
    }
}

data class TriangleIndices(val a: Int, val b: Int, val c: Int) {
    override fun toString(): String {
        return "<$a,$b,$c>"
    }
}

class TriangleIndexArray(val array: IntArray) {
    companion object {
        fun of(vararg indices: Int) = TriangleIndexArray(indices)
    }

    constructor(points: List<TriangleIndices>) : this(
        IntArray(points.size * 3).also { arr ->
            var i = 0
            for (point in points) {
                arr[i++] = point.a
                arr[i++] = point.b
                arr[i++] = point.c
            }
        }
    )

    val size get() = array.size

    override fun toString(): String {
        return toList().toString()
    }

    fun toList(): List<TriangleIndices> = buildList {
        forEachTriangle { x, y, z ->
            add(TriangleIndices(x, y, z))
        }
    }

    inline fun forEachTriangle(iter: (a: Int, b: Int, c: Int) -> Unit) {
        var i = 0
        while (i < array.size) {
            iter(array[i++], array[i++], array[i++])
        }
    }

    inline fun forEach(iter: (i: Int) -> Unit) = array.forEach(iter)

//    inline fun mapTriangles(transform: (a: Int, b: Int, c: Int))
}
