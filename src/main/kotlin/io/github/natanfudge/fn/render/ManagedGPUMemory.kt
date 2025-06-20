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
class ManagedGPUMemory(val ctx: WebGPUContext, val initialSizeBytes: ULong, expandable: Boolean, vararg usage: GPUBufferUsage) : AutoCloseable {
    private var _nextByte = 0uL
    private var currentMemoryLimit = initialSizeBytes.wgpuAlign()
    fun alloc(bytes: ULong): UntypedGPUPointer {
        val address = _nextByte
        if (address + bytes > currentMemoryLimit) {
            throw NotImplementedError("Dynamic Memory expansion is not implemented yet (limit: $currentMemoryLimit, requested: $address + $bytes)")
        }
        _nextByte += bytes
        return UntypedGPUPointer(address)
    }

    fun free(pointer: UntypedGPUPointer, size: UInt) {
        // SLOW: currently a no-op, so a memory leak, i'll do this later.
    }


    fun write(data: KByteBuffer, address: UntypedGPUPointer = UntypedGPUPointer(0uL)) {
        ctx.device.queue.writeBuffer(buffer, address.address, data.array())
    }


    operator fun <T : GPUStructDescriptor> set(address: GPUPointer<T>, data: GPUStruct<T>) {
        write(data.buffer, address)
    }

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



typealias KByteBuffer = ByteBuffer








 private fun Any.arrayByteSize() = when (this) {
    is IntArray -> size * 4
    is ByteArray -> size
    is FloatArray -> size * 4
    else -> error("Unsupported array type $this")
}

