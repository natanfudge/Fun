package io.github.natanfudge.fn.webgpu

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.Origin3D
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.asArrayBuffer
import io.ygdrasil.webgpu.mapFrom
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
// Multiplatform variant of the other one, don't delete this (slow but might be useful)
//fun ByteArray.toArrayBuffer(device: GPUDevice, usage: (ArrayBuffer) -> Unit) {
//    floatArrayOf(1f,2f,3f)
//    device.createBuffer(
//        BufferDescriptor(
//            size = size.toULong(),
//            usage = setOf(GPUBufferUsage.CopySrc, GPUBufferUsage.CopyDst),
//            mappedAtCreation = true
//        )
//    ).use { gpuBuffer ->
//        // Copy from RAM to GPU
//        gpuBuffer.mapFrom(this)
//        // Copy from GPU to RAM, ideally we can just directly create an ArrayBuffer from RAM
//        usage(gpuBuffer.getMappedRange())
//    }
//}
// Workaround for https://github.com/wgpu4k/wgpu4k/issues/132, once that is resolved you should use the function provided by wgpu4k
 inline fun ByteArray.toArrayBuffer(action: (ArrayBuffer) -> Unit) = Arena.ofConfined().use { arena ->
    val byteSizeToCopy = (this.size).toLong()
    val segment = arena.allocate(byteSizeToCopy)
    MemorySegment.copy(MemorySegment.ofArray(this), 0, segment, 0, byteSizeToCopy)
    segment.asArrayBuffer(byteSizeToCopy)
        .let(action)
}