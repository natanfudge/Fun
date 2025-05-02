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
import io.ygdrasil.webgpu.mapFrom
import kotlin.use

/**
 * Temporary replacement for the web copyExternalImageToTexture that is not available in other platforms
 */
fun GPUDevice.copyExternalImageToTexture(source: ByteArray, texture: GPUTexture, width: Int, height: Int, origin: Origin3D = Origin3D()) {
    source.toArrayBuffer(this) { buffer ->
        queue.writeTexture(
            TexelCopyTextureInfo(texture, origin = origin),
            data = buffer,
            dataLayout = TexelCopyBufferLayout(bytesPerRow = width.toUInt() * 4u, rowsPerImage = height.toUInt()),
            size = Extent3D(width = width.toUInt(), height = height.toUInt())
        )
    }
}

// Workaround for https://github.com/wgpu4k/wgpu4k/issues/132, once that is resolved you should use the function provided by wgpu4k
fun ByteArray.toArrayBuffer(device: GPUDevice, usage: (ArrayBuffer) -> Unit) {
    device.createBuffer(
        BufferDescriptor(
            size = size.toULong(),
            usage = setOf(GPUBufferUsage.CopySrc, GPUBufferUsage.CopyDst),
            mappedAtCreation = true
        )
    ).use { gpuBuffer ->
        // Copy from RAM to GPU
        gpuBuffer.mapFrom(this)
        // Copy from GPU to RAM, ideally we can just directly create an ArrayBuffer from RAM
        usage(gpuBuffer.getMappedRange())
    }
}