@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.render

import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.wgpu4k.matrix.Mat3f
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import io.github.natanfudge.wgpu4k.matrix.Vec4f
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.writeBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder


typealias UntypedGPUPointer = GPUPointer<Any?>

@JvmInline
value class GPUPointer<out T>(
    val address: ULong,
) {
    operator fun plus(offset: ULong) = GPUPointer<T>(address + offset)
    operator fun plus(offset: UInt) = GPUPointer<T>(address + offset)
}

//TODO: for some usages, we don't need to recreate a render group so just changing the buffer pointer would be fine - for those usages enable auto-resizing.
// for other usages, we could just set a decent limit and throw when we try to resize, saying "We are waiting for bindless/mutable bind groups to enable doing this simply and performantly"
class ManagedGPUMemory(val ctx: WebGPUContext, initialSizeBytes: ULong, vararg usage: GPUBufferUsage) : AutoCloseable {
    private var _nextByte = 0uL
    private var currentMemoryLimit = initialSizeBytes.wgpuAlign()
    fun alloc(bytes: ULong): UntypedGPUPointer {
        val address = _nextByte
        if (address + bytes > currentMemoryLimit) {
            TODO("Dynamic Memory expansion is not implemented yet")
        }
        _nextByte += bytes
        return UntypedGPUPointer(address)
    }

    fun write(data: KByteBuffer, address: UntypedGPUPointer = UntypedGPUPointer(0uL)) {
        ctx.device.queue.writeBuffer(buffer, address.address, data.array())
    }


    operator fun <T : GPUStructDescriptor> set(address: GPUPointer<T>, data: GPUStruct<T>) {
        write(data.buffer, address)
    }

    //TODO: remove a lot of functions here
    fun new(data: Any): UntypedGPUPointer {
        val pointer = alloc(data.arrayByteSize().toULong())
        write(data, pointer)
        return pointer
    }

    fun write(bytes: FloatArray, pointer: UntypedGPUPointer = UntypedGPUPointer(0uL)) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }

    fun write(bytes: IntArray, pointer: UntypedGPUPointer = UntypedGPUPointer(0uL)) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }

    fun write(bytes: ByteArray, pointer: UntypedGPUPointer = UntypedGPUPointer(0uL)) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }

    fun write(array: Any, pointer: UntypedGPUPointer) = when (array) {
        is FloatArray -> write(array, pointer)
        is IntArray -> write(array, pointer)
        is ByteArray -> write(array, pointer)
        else -> error("Unsupported datatype: $array")
    }


    fun new(data: FloatArray): UntypedGPUPointer {
        val pointer = alloc(data.byteSize().wgpuAlign())
        write(data, pointer)
        return pointer
    }

    fun new(data: IntArray): UntypedGPUPointer {
        val pointer = alloc(data.byteSize().wgpuAlign())
        write(data, pointer)
        return pointer
    }

    val buffer = ctx.device.createBuffer(
        BufferDescriptor(
            size = initialSizeBytes.wgpuAlign(),
            usage = usage.toSet() + setOf(GPUBufferUsage.CopyDst)
        )
    )

    override fun close() {
        buffer.close()
    }
}


private fun FloatArray.byteSize() = (size * Float.SIZE_BYTES).toULong()
private fun IntArray.byteSize() = (size * Int.SIZE_BYTES).toULong()

class GPUStruct<T : GPUStructDescriptor>(
    internal val buffer: ByteArray,
    internal val descriptor: T,
)

typealias KByteBuffer = ByteBuffer

interface GPUStructDescriptor {
    val size: UInt
}


abstract class Struct4<T1, T2, T3, T4, S : Struct4<T1, T2, T3, T4, S>>(
    val t1: DataType<T1>,
    val t2: DataType<T2>,
    val t3: DataType<T3>,
    val t4: DataType<T4>,
) : GPUStructDescriptor {

    val t2Start = t1.alignSize
    val t3Start = t2Start + t2.alignSize
    val t4Start = t3Start + t3.alignSize
    override val size = (t4Start + t4.alignSize).wgpuAlignInt()

    private fun toArray(a: T1, b: T2, c: T3, d: T4) = concatDifferentArrays(
        size, t1.toArray(a), t2.toArray(b), t3.toArray(c), t4.toArray(d)
    )

    operator fun invoke(a: T1, b: T2, c: T3, d: T4): GPUStruct<S> {
        return GPUStruct(
            toArray(a,b,c,d),
            this
        ) as GPUStruct<S>
    }

    fun new(mem: ManagedGPUMemory, a: T1, b: T2, c: T3, d: T4): GPUPointer<S> {
        return mem.new(toArray(a,b,c,d)) as GPUPointer<S>
    }

    fun setFirst(mem: ManagedGPUMemory, pointer: GPUPointer<S>, a: T1) {
        mem.write(t1.toArray(a), pointer)
    }

    fun setSecond(mem: ManagedGPUMemory, pointer: GPUPointer<S>, b: T2) {
        mem.write(t2.toArray(b), pointer + t2Start)
    }

    fun setThird(mem: ManagedGPUMemory, pointer: GPUPointer<S>, c: T3) {
        mem.write(t3.toArray(c), pointer + t3Start)
    }

    fun setFourth(mem: ManagedGPUMemory, pointer: GPUPointer<S>, d: T4) {
        mem.write(t4.toArray(d), pointer + t4Start)
    }

    fun createBuffer(ctx: WebGPUContext, initialSize: UInt, vararg usage: GPUBufferUsage): ManagedGPUMemory {
        return ManagedGPUMemory(ctx, initialSizeBytes = (initialSize * size.toULong()), *usage)
    }

}

fun concatDifferentArrays(resArraySize: UInt, vararg arrays: Any): ByteArray {
    val buffer = createMemoryBuffer(resArraySize)
    var offset = 0
    for (array in arrays) {
        buffer.putArray(offset, array)
        offset += array.arrayByteSize()
    }
    return buffer.array()
}

private fun createMemoryBuffer(size: UInt): KByteBuffer {
    val array = ByteArray(size.toInt())
    // GPU uses little endian apparently
    val buffer = ByteBuffer.wrap(array).order(ByteOrder.LITTLE_ENDIAN)
    return buffer
}

private fun ByteBuffer.putArray(offset: Int, array: Any) = when (array) {
    is IntArray -> asIntBuffer().put(offset / 4, array)
    is ByteArray -> put(offset, array)
    is FloatArray -> asFloatBuffer().put(offset / 4, array)
    else -> error("Unsupported array type $this")
}

private fun Any.arrayByteSize() = when (this) {
    is IntArray -> size * 4
    is ByteArray -> size
    is FloatArray -> size * 4
    else -> error("Unsupported array type $this")
}

interface DataType<T> {
    val alignSize: UInt
    fun toArray(value: T): Any
}


object Mat4fDT : DataType<Mat4f> {
    override val alignSize: UInt = Mat4f.SIZE_BYTES
    override fun toArray(value: Mat4f): Any = value.array
}

object Mat3fDT : DataType<Mat3f> {
    override val alignSize: UInt = Mat3f.SIZE_BYTES
    override fun toArray(value: Mat3f): Any = value.array
}

object FloatDT : DataType<Float> {
    override val alignSize: UInt = Float.SIZE_BYTES.toUInt()
    override fun toArray(value: Float): Any = floatArrayOf(value)
}

object Vec3fDT : DataType<Vec3f> {
    override val alignSize: UInt = Vec3f.ALIGN_BYTES
    override fun toArray(value: Vec3f): Any = value.toAlignedArray()
}

object Vec4fDT : DataType<Vec4f> {
    override val alignSize: UInt = Vec4f.SIZE_BYTES
    override fun toArray(value: Vec4f): Any = value.toArray()
}

object ColorDT : DataType<Color> {
    override val alignSize: UInt = Vec4f.SIZE_BYTES
    override fun toArray(value: Color): Any = floatArrayOf(value.red, value.green, value.blue, value.alpha)
}

object IntDT : DataType<Int> {
    override val alignSize: UInt = Int.SIZE_BYTES.toUInt()
    override fun toArray(value: Int): Any {
        return intArrayOf(value)
    }
}
