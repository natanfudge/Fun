package io.github.natanfudge.fn.render

import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

typealias Point3f = Vec3f

class Mesh(val indices: TriangleIndexArray, val vertices: VertexArrayBuffer) {
    companion object {
        /**
         * Automatically infers normals for all vertices.
         * Note that the "smoothness" of the shading depends on how much the indices are shared between vertices.
         * If all indices use separate vertices, the shading will be flat.
         * If vertices share indices, the normal will be average out for that index, making the shading smooth.
         */
        fun withNormals(
            indices: TriangleIndexArray,
            positions: List<Point3f>,
            uv: List<UV>
        ): Mesh {
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

            val vbo = VertexArrayBuffer.of(positions, normals, uv)
            return Mesh(indices, vbo)
        }


        // There is actually only 24 unique positions, we don't need 36
        /**
         * Creates a cube that partially shares vertices, in a way that has the minimum amount of vertices, but allows for correct flat shading.
         */
        fun UnitCube() = Mesh.withNormals(
            positions = listOf(
                // front (Z = 1)
                Vec3f(-0.5f, -0.5f, 0.5f), Vec3f(0.5f, -0.5f, 0.5f), Vec3f(0.5f, 0.5f, 0.5f), Vec3f(-0.5f, 0.5f, 0.5f),
                // back (Z = 0)
                Vec3f(0.5f, -0.5f, -0.5f), Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(-0.5f, 0.5f, -0.5f), Vec3f(0.5f, 0.5f, -0.5f),
                // left (X = 0)
                Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(-0.5f, -0.5f, 0.5f), Vec3f(-0.5f, 0.5f, 0.5f), Vec3f(-0.5f, 0.5f, -0.5f),
                // right (X = 1)
                Vec3f(0.5f, -0.5f, 0.5f), Vec3f(0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f),
                // top (Y = 1)
                Vec3f(-0.5f, 0.5f, 0.5f), Vec3f(0.5f, 0.5f, 0.5f), Vec3f(0.5f, 0.5f, -0.5f), Vec3f(-0.5f, 0.5f, -0.5f),
                // bottom (Y = 0)
                Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, -0.5f, -0.5f), Vec3f(0.5f, -0.5f, 0.5f), Vec3f(-0.5f, -0.5f, 0.5f)
            ),
            uv = listOf(
                // front (Z = 1)
                UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                // back (Z = 0)
                UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                // left (X = 0)
                UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                // right (X = 1)
                UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                // top (Y = 1)
                UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f),
                // bottom (Y = 0)
                UV(0f, 1f), UV(1f, 1f), UV(1f, 0f), UV(0f, 0f)
            ),
            indices = TriangleIndexArray.of(
                0, 1, 2, 0, 2, 3,      // front
                4, 5, 6, 4, 6, 7,      // back
                8, 9, 10, 8, 10, 11,      // left
                12, 13, 14, 12, 14, 15,      // right
                16, 17, 18, 16, 18, 19,      // top
                20, 21, 22, 20, 22, 23       // bottom
            )
        )


        /**
         * Creates a sphere that shares vertices as much as possible, making shading smooth.
         */
        fun sphere(segments: Int = 64): Mesh {
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

            return Mesh.withNormals(
                positions = vertices,
                indices = TriangleIndexArray(indices.toIntArray()),
                uv = buildList {
                    for (i in 0..segments) {
                        val v = i.toFloat() / segments

                        for (j in 0 until segments) {
                            val u = j.toFloat() / segments
                            add(UV(u, v))
                        }
                    }
                }
            )
        }
    }

    val triangles get() = vertices.size / VertexArrayBuffer.StrideFloats
    val vertexCount = vertices.size.toULong()
    val indexCount = indices.size.toULong()
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

}


data class ModelTriangle(val a: Vertex, val b: Vertex, val c: Vertex) {
    override fun toString(): String {
        return "<$a,$b,$c>"
    }
}

data class Vertex(val pos: Point3f, val normal: Vec3f, val uv: UV) {
    override fun toString(): String {
        return "pos=$pos,norm=$normal"
    }
}

data class TriangleF(
    val a: Point3f,
    val b: Point3f,
    val c: Point3f,
)

/**
 * Determine the direction of a face of a mesh (a  triangle [a], [b], [c])
 * Assumes the triangle is in counter-clockwise order, otherwise this vector will incorrectly point inwards instead of outwards.
 */
private fun inferNormal(a: Point3f, b: Point3f, c: Point3f): Vec3f {
    return (b - a).cross(c - a).normalize()
//    val diff1 = b - a
//    val diff2 = c - a
//    val cross = diff1.cross(diff2, diff1)
//    val normalized = cross.normalize(cross)
//    return normalized
}


data class UV(val u: Float, val v: Float)

class VertexArrayBuffer(val array: FloatArray) {
    companion object {
        const val StrideFloats = 8
        val StrideBytes = (StrideFloats * Float.SIZE_BYTES).toULong()

        //        fun of(vararg floats: Float) = VertexArrayBuffer(floats)
        fun of(positions: List<Point3f>, normals: List<Vec3f>, uv: List<UV>): VertexArrayBuffer {
            require(normals.size == positions.size)
            require(uv.size == positions.size)
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
            UV(array[i++], array[i])
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
                    UV(array[i++], array[i++])
                )
            )
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

//    inline fun mapTriangles(transform: (a: Int, b: Int, c: Int))
}
