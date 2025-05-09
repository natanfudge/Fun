package io.github.natanfudge.fn.render

import io.github.natanfudge.wgpu4k.matrix.Vec3f

typealias Point3f = Vec3f

class Mesh(val indices: TriangleIndexArray, val vertices: PosArray) {
    companion object {
        val UnitCube = Mesh(
            // There is actually only 24 unique positions, we don't need 36
            vertices = PosArray.of(
                // front (Z = 1)
                0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 1f,
                // back (Z = 0)
                1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f,
                // left (X = 0)
                0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0f,
                // right (X = 1)
                1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f,
                // top (Y = 1)
                0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 0f,
                // bottom (Y = 0)
                0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f
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
    }

    val triangles get() = vertices.size / 3
    val vertexCount = vertices.size.toULong()
    val indexCount = indices.size.toULong()

    override fun toString(): String {
        return toTriangleList().toString()
    }

    fun toTriangleList(): List<TriangleF> = buildList {
        forEachTriangle { a, b, c ->
            add(TriangleF(a, b, c))
        }
    }

    inline fun forEachTriangle(iter: (a: Point3f, b: Point3f, c: Point3f) -> Unit) {
        indices.forEachTriangle { a, b, c ->
            iter(
                vertices[a], vertices[b], vertices[c]
            )
        }
    }

}


data class TriangleF(val a: Point3f, val b: Point3f, val c: Point3f) {
    override fun toString(): String {
        return "<$a,$b,$c>"
    }
}

class PosArray(val array: FloatArray) {
    companion object {
        fun of(vararg floats: Float) = PosArray(floats)
    }

    constructor(points: List<Point3f>) : this(
        FloatArray(points.size * 3).also { arr ->
            var i = 0
            for (point in points) {
                arr[i++] = point.x
                arr[i++] = point.y
                arr[i++] = point.z
            }
        }
    )

    val size get() = array.size / 3

    operator fun get(index: Int): Point3f {
        var i = index * 3
        return Point3f(array[i++], array[i++], array[i])
    }

    override fun toString(): String {
        return toList().toString()
    }

    fun toList(): List<Point3f> = buildList {
        forEachPoint { x, y, z ->
            add(Point3f(x, y, z))
        }
    }

    inline fun forEachPoint(iter: (x: Float, y: Float, z: Float) -> Unit) {
        var i = 0
        while (i < array.size) {
            iter(array[i++], array[i++], array[i++])
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
}



