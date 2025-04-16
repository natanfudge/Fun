package io.github.natanfudge.fu.math

data class Mat4f(
    val a1: Float, val a2: Float, val a3: Float, val a4: Float,
    val b1: Float, val b2: Float, val b3: Float, val b4: Float,
    val c1: Float, val c2: Float, val c3: Float, val c4: Float,
    val d1: Float, val d2: Float, val d3: Float, val d4: Float,
) {
    operator fun times(v: Vec4f): Vec4f {
        return Vec4f(
            a1 * v.x + a2 * v.y + a3 * v.z + a4 * v.w,
            b1 * v.x + b2 * v.y + b3 * v.z + b4 * v.w,
            c1 * v.x + c2 * v.y + c3 * v.z + c4 * v.w,
            d1 * v.x + d2 * v.y + d3 * v.z + d4 * v.w
        )
    }
}

data class Vec4f(val x: Float, val y: Float, val z: Float, val w: Float) {
    constructor(x: Int, y: Int, z: Int, w: Int) : this(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
    override fun toString(): String {
        return "($x $y $z $w)"
    }
}