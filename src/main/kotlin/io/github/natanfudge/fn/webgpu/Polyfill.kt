package io.github.natanfudge.fn.webgpu

import ffi.MemoryBuffer
import ffi.NativeAddress
import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.Buffer
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUSize64
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.Origin3D
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.asArrayBuffer
import io.ygdrasil.webgpu.writeInto
import io.ygdrasil.wgpu.wgpuBufferGetMappedRange
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.use

/**
 * Temporary replacement for the web copyExternalImageToTexture that is not available in other platforms
 */
fun GPUDevice.copyExternalImageToTexture(source: ByteArray, texture: GPUTexture, width: Int, height: Int, origin: Origin3D = Origin3D()) {
    source.toArrayBuffer { buffer ->
        queue.writeTexture(
            TexelCopyTextureInfo(texture, origin = origin),
            data = buffer,
            dataLayout = TexelCopyBufferLayout(bytesPerRow = width.toUInt() * 4u, rowsPerImage = height.toUInt()),
            size = Extent3D(width = width.toUInt(), height = height.toUInt())
        )
    }
}

// Workaround for https://github.com/wgpu4k/wgpu4k/issues/132, once that is resolved you should use the function provided by wgpu4k
 inline fun ByteArray.toArrayBuffer(action: (ArrayBuffer) -> Unit) = Arena.ofConfined().use { arena ->
    val byteSizeToCopy = (this.size).toLong()
    val segment = arena.allocate(byteSizeToCopy)
    MemorySegment.copy(MemorySegment.ofArray(this), 0, segment, 0, byteSizeToCopy)
    segment.asArrayBuffer(byteSizeToCopy)
        .let(action)
}

// Hopefully there will be an api for this in the future
fun GPUBuffer.mapFrom(buffer: IntArray, offset: GPUSize64 = 0u): GPUBuffer {
//    IntArray(123).
    buffer.writeInto(getMappedRange(offset))
    return this
}

fun GPUBuffer.mapFrom(buffer: FloatArray, offset: GPUSize64 = 0u): GPUBuffer {
    buffer.writeInto(getMappedRange(offset))
    return this
}

private fun NativeAddress?.asBuffer(size: ULong): MemoryBuffer =
    MemoryBuffer((this ?: error("buffer should not be null")), size)