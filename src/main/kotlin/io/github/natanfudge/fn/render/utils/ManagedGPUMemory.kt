@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.render.utils

import io.github.natanfudge.fn.render.wgpuAlign
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.writeBuffer
import java.nio.ByteBuffer


typealias UntypedGPUPointer = GPUPointer<Any?>

@JvmInline
value class GPUPointer<out T>(
    val address: ULong,
) {
    operator fun plus(offset: ULong) = GPUPointer<T>(address + offset)
    operator fun plus(offset: UInt) = GPUPointer<T>(address + offset)
}


class ManagedGPUMemory(val ctx: WebGPUContext, val initialSizeBytes: ULong, expandable: Boolean, vararg usage: GPUBufferUsage) : AutoCloseable {
    val fullBytes get() = _nextByte
    private var _nextByte = 0uL
    // Buffer must be at least 64 bytes
    private var currentMemoryLimit = (initialSizeBytes.coerceAtLeast(64u)).wgpuAlign()
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


    operator fun <T : GPUStructDescriptor> set(address: GPUPointer<T>, data: GPUStructInstance<T>) {
        write(data.buffer, address)
    }

    fun new(data: Any): UntypedGPUPointer {
        val pointer = alloc(arrayByteSize(data).toULong())
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
            size = currentMemoryLimit,
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








 internal fun arrayByteSize(array: Any) = when (array) {
    is IntArray -> array.size * 4
    is ByteArray -> array.size
    is FloatArray -> array.size * 4
    else -> error("Unsupported array type $array")
}

