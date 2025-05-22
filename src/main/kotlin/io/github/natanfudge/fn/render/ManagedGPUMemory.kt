package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.writeBuffer
import java.lang.invoke.MethodHandles
import java.nio.ByteBuffer
import java.nio.ByteOrder


@JvmInline
value class GPUPointer(
    val address: ULong,
)

//TODO: for some usages, we don't need to recreate a render group so just changing the buffer pointer would be fine - for those usages enable auto-resizing.
// for other usages, we could just set a decent limit and throw when we try to resize, saying "We are waiting for bindless/mutable bind groups to enable doing this simply and performantly"
class ManagedGPUMemory(val ctx: WebGPUContext, initialSizeBytes: ULong, vararg usage: GPUBufferUsage) : AutoCloseable {
    private var _nextByte = 0uL
    private var currentMemoryLimit = initialSizeBytes.wgpuAlign()
    fun alloc(bytes: ULong): GPUPointer {
        val address = _nextByte
        if (address + bytes > currentMemoryLimit) {
            TODO("Dynamic Memory expansion is not implemented yet")
        }
        _nextByte += bytes
        return GPUPointer(address)
    }

    fun write(bytes: FloatArray, pointer: GPUPointer = GPUPointer(0uL)) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }

    fun write(bytes: IntArray, pointer: GPUPointer = GPUPointer(0uL)) {
        ctx.device.queue.writeBuffer(buffer, pointer.address, bytes)
    }


    //TODO: first step is go KByteBuffer on the API here, to simplify it, and the struct abstraction gives it the correct KByteBuffer
    fun new(data: FloatArray): GPUPointer {
        val pointer = alloc(data.byteSize().wgpuAlign())
        write(data, pointer)
        return pointer
    }

    fun new(data: IntArray): GPUPointer {
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


typealias KByteBuffer = ByteBuffer

interface GPUStruct {
    val size: UInt
}

class Struct4<T1,T2,T3,T4>(
    val t1: DataType<T1>,
    val t2: DataType<T2>,
    val t3: DataType<T3>,
    val t4: DataType<T4>,
) {
    val size = t1.alignSize + t2.alignSize + t3.alignSize + t4.alignSize

    fun create(a: T1, b: T2, c: T3, d: T4): KByteBuffer {
        TODO()
    }
}

interface DataType<T> {
    val byteSize: UInt
    val alignSize: UInt get() = byteSize
    fun write(value: T, buffer: KByteBuffer, offset: UInt)
}

object Mat4fDT: DataType<Mat4f> {
    override val byteSize: UInt
        get() = TODO("Not yet implemented")

    override fun write(value: Mat4f, buffer: KByteBuffer, offset: UInt) {
        TODO("Not yet implemented")
    }
}

object FloatDT: DataType<Float> {
    override val byteSize: UInt
        get() = TODO("Not yet implemented")

    override fun write(value: Float, buffer: KByteBuffer, offset: UInt) {
        TODO("Not yet implemented")
    }

}