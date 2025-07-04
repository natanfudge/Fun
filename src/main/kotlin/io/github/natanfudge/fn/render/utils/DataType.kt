package io.github.natanfudge.fn.render.utils

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.github.natanfudge.wgpu4k.matrix.Vec4f

interface DataType<T> {
    val actualSize: UInt
    val alignSize: UInt get() = actualSize
    fun toArray(value: T): Any
}


object Mat4fDT : DataType<Mat4f> {
    override val actualSize: UInt = Mat4f.SIZE_BYTES
    override fun toArray(value: Mat4f): Any = value.array
}

object Mat3fDT : DataType<Mat3f> {
    override val actualSize: UInt = Mat3f.SIZE_BYTES
    override fun toArray(value: Mat3f): Any = value.array
}

object FloatDT : DataType<Float> {
    override val actualSize: UInt = Float.SIZE_BYTES.toUInt()
    override fun toArray(value: Float): Any = floatArrayOf(value)
}

object Vec3fDT : DataType<Vec3f> {
    override val actualSize: UInt = Vec3f.ACTUAL_SIZE_BYTES
    override val alignSize: UInt = Vec3f.ALIGN_BYTES
    override fun toArray(value: Vec3f): Any = value.toAlignedArray()
}

object Vec4fDT : DataType<Vec4f> {
    override val actualSize: UInt = Vec4f.SIZE_BYTES
    override fun toArray(value: Vec4f): Any = value.toArray()
}

object ColorDT : DataType<Color> {
    override val actualSize: UInt = Vec4f.SIZE_BYTES
    override fun toArray(value: Color): Any = floatArrayOf(value.red, value.green, value.blue, value.alpha)
}

class Mat4fArrayDT(val arraySize: Int): DataType<List<Mat4f>> {
    override val actualSize: UInt = Mat4f.SIZE_BYTES * arraySize.toUInt()
    override fun toArray(value: List<Mat4f>): Any  = value.toFloatArray()
}

 fun List<Mat4f>.toFloatArray(): FloatArray {
    val array = FloatArray(size * Mat4f.ELEMENT_COUNT.toInt())
    forEachIndexed { i, matrix ->
        val offset = i * Mat4f.ELEMENT_COUNT.toInt()
        matrix.array.copyInto(array, offset)
    }
    return array
}

object IntDT : DataType<Int> {
    override val actualSize: UInt = Int.SIZE_BYTES.toUInt()
    override fun toArray(value: Int): Any {
        return intArrayOf(value)
    }
}
object UIntDT : DataType<UInt> {
    override val actualSize: UInt = Int.SIZE_BYTES.toUInt()
    override fun toArray(value: UInt): Any {
        val result = intArrayOf(value.toInt())
//        println("UIntDT.toArray: $value -> ${result.contentToString()}")
        return result
    }
}
