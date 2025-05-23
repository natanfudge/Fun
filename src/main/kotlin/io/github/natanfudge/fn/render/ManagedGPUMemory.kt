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
)

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


    fun new(data: KByteBuffer, size: ULong): UntypedGPUPointer {
        val pointer = alloc(size)
        write(data, pointer)
        return pointer
    }

    fun write(bytes: FloatArray, pointer: UntypedGPUPointer = UntypedGPUPointer(0uL)) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }

    fun write(bytes: IntArray, pointer: UntypedGPUPointer = UntypedGPUPointer(0uL)) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }


    //TODO: first step is go KByteBuffer on the API here, to simplify it, and the struct abstraction gives it the correct KByteBuffer
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
    internal val buffer: ByteBuffer,
    internal val descriptor: T,
)

typealias KByteBuffer = ByteBuffer

interface GPUStructDescriptor {
    val size: UInt
}

private fun createMemoryBuffer(size: UInt): KByteBuffer {
    val array = ByteArray(size.toInt())
    // GPU uses little endian apparently
    val buffer = ByteBuffer.wrap(array).order(ByteOrder.LITTLE_ENDIAN)
    return buffer
}


abstract class Struct4<T1, T2, T3, T4>(
    val t1: DataType<T1>,
    val t2: DataType<T2>,
    val t3: DataType<T3>,
    val t4: DataType<T4>,
) : GPUStructDescriptor {

//    typealias Self = Struct4<T1,T2,T3,T4>

    val t2Start = t1.alignSize
    val t3Start = t2Start + t2.alignSize
    val t4Start = t3Start + t3.alignSize
    override val size = t4Start + t4.alignSize

    private fun write(a: T1, b: T2, c: T3, d: T4, buffer: KByteBuffer) {
        t1.write(a, buffer, 0u)
        t2.write(b, buffer, t2Start)
        t3.write(c, buffer, t3Start)
        t4.write(d, buffer, t4Start)
    }

    // This is technically duck typing
    operator fun invoke(a: T1, b: T2, c: T3, d: T4): GPUStruct<Struct4<T1, T2, T3, T4>> {
        // GPU uses little endian apparently
        val buffer = createMemoryBuffer(size)
        write(a, b, c, d, buffer)
        return GPUStruct(
            buffer,
            this
        )
    }

    fun new(mem: ManagedGPUMemory, a: T1, b: T2, c: T3, d: T4): GPUPointer<Struct4<T1, T2, T3, T4>> {
        val buffer = createMemoryBuffer(size)
        write(a, b, c, d, buffer)
        return mem.new(buffer, size.wgpuAlign()) as GPUPointer<Struct4<T1, T2, T3, T4>>
    }

    fun setFirst(mem: ManagedGPUMemory, pointer: GPUPointer<Struct4<T1, T2, T3, T4>>, a: T1) {
        TODO()
    }

    fun setSecond(mem: ManagedGPUMemory, pointer: GPUPointer<Struct4<T1, T2, T3, T4>>, b: T2) {
        TODO()
    }

    fun setThird(mem: ManagedGPUMemory, pointer: GPUPointer<Struct4<T1, T2, T3, T4>>, c: T3) {
        TODO()
    }

    fun setFourth(mem: ManagedGPUMemory, pointer: GPUPointer<Struct4<T1, T2, T3, T4>>, d: T4) {
        TODO()
    }

}

interface DataType<T> {
    val alignSize: UInt
    fun write(value: T, buffer: KByteBuffer, offset: UInt)
}

object Mat4fDT : DataType<Mat4f> {
    override val alignSize: UInt = Mat4f.SIZE_BYTES

    override fun write(value: Mat4f, buffer: KByteBuffer, offset: UInt) {
        buffer.asFloatBuffer().put(offset.toInt() / Float.SIZE_BYTES, value.array)
    }
}

object Mat3fDT : DataType<Mat3f> {
    override val alignSize: UInt = Mat3f.SIZE_BYTES

    override fun write(value: Mat3f, buffer: KByteBuffer, offset: UInt) {
        buffer.asFloatBuffer().put(offset.toInt() / Float.SIZE_BYTES, value.array)
    }

}

object FloatDT : DataType<Float> {
    override val alignSize: UInt = Float.SIZE_BYTES.toUInt()

    override fun write(value: Float, buffer: KByteBuffer, offset: UInt) {
        buffer.putFloat(offset.toInt(), value)
    }
}

object Vec3fDT : DataType<Vec3f> {
    override val alignSize: UInt = Vec3f.ALIGN_BYTES
    override fun write(value: Vec3f, buffer: KByteBuffer, offset: UInt) {
        buffer.putFloat(offset.toInt(), value.x)
        buffer.putFloat(offset.toInt() + 4, value.y)
        buffer.putFloat(offset.toInt() + 8, value.z)
    }
}

object Vec4fDT : DataType<Vec4f> {
    override val alignSize: UInt = Vec4f.SIZE_BYTES
    override fun write(value: Vec4f, buffer: KByteBuffer, offset: UInt) {
        buffer.putFloat(offset.toInt(), value.x)
        buffer.putFloat(offset.toInt() + 4, value.y)
        buffer.putFloat(offset.toInt() + 8, value.z)
        buffer.putFloat(offset.toInt() + 12, value.w)
    }
}

object ColorDT : DataType<Color> {
    override val alignSize: UInt = Vec4f.SIZE_BYTES
    override fun write(value: Color, buffer: KByteBuffer, offset: UInt) {
        buffer.putFloat(offset.toInt(), value.red)
        buffer.putFloat(offset.toInt() + 4, value.green)
        buffer.putFloat(offset.toInt() + 8, value.blue)
        buffer.putFloat(offset.toInt() + 12, value.alpha)
    }
}


//struct Instance {
//    model: mat4x4f,
//    normalMat: mat3x3f,
//    color: vec4f,
//    textured: f32 // This is a bool, but we'll use a float to keep it all floats
//}