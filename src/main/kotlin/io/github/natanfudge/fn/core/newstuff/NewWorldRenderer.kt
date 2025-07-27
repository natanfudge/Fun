package io.github.natanfudge.fn.core.newstuff

import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.render.utils.ManagedGPUMemory
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import io.ygdrasil.webgpu.*
import kotlin.math.PI

fun IntSize.toExtend3D(depthOrArrayLayers: Int = 1) = Extent3D(width.toUInt(), height = height.toUInt(), depthOrArrayLayers.toUInt())

class WRWebGPUBinding(ctx: NewWebGPUContext) : AutoCloseable {

     //TODO: this stuff depends on a certain size (needs to be in a separate class, need to see how to nicely integrate it)
    val extent = ctx.window.size.toExtend3D()

    // Create z buffer
    val depthTexture = ctx.device.createTexture(
        TextureDescriptor(
            size = extent,
            format = GPUTextureFormat.Depth24Plus,
            usage = setOf(GPUTextureUsage.RenderAttachment),
            sampleCount = msaaSamples,
            label = "Depth Texture"
        )
    )
    val depthStencilView = depthTexture.createView()

    val msaaTexture = ctx.device.createTexture(
        TextureDescriptor(
            size = extent,
            sampleCount = msaaSamples,
            format = ctx.presentationFormat,
            usage = setOf(GPUTextureUsage.RenderAttachment),
            label = "MSAA Texture"
        )
    )

    val msaaTextureView = msaaTexture.createView()
    // closeAll(depthTexture, depthStencilView, msaaTexture, msaaTextureView, )
    //TODO: size dependency end

    val uniformBuffer = WorldUniform.createBuffer(ctx, 1u, expandable = false, GPUBufferUsage.Uniform)
    val vertexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 100_000_000u, expandable = true, GPUBufferUsage.Vertex)
    val indexBuffer = ManagedGPUMemory(ctx, initialSizeBytes = 20_000_000u, expandable = true, GPUBufferUsage.Index)

    val models = mutableMapOf<ModelId, BoundModel>()
    val rayCasting = RayCastingCache<RenderInstance>()

    internal val baseInstanceData: IndirectInstanceBuffer = IndirectInstanceBuffer(
        ctx, models, maxInstances = 50_000, expectedElementSize = BaseInstanceData.size
    ) { it.renderId }
    internal val jointMatrixData: IndirectInstanceBuffer =
        IndirectInstanceBuffer(ctx, models, maxInstances = 1_000, expectedElementSize = Mat4f.SIZE_BYTES * 50u) {
            (it.jointMatricesPointer.address / Mat4f.SIZE_BYTES).toInt()
        }


    override fun close() {
        closeAll(

            vertexBuffer, indexBuffer, uniformBuffer, baseInstanceData, jointMatrixData
        )
        models.forEach { it.value.close() }
    }
}

val IntSize.aspectRatio get() = width.toFloat() / height


class NewWorldRenderer(val surface: NewWebGPUSurface) : NewFun("WorldRenderer") {


    private fun calculateProjectionMatrix() = Mat4f.perspective(
        fieldOfViewYInRadians = fovYRadians,
        aspect = surface.size.aspectRatio,
        zNear = 0.01f,
        zFar = 100f
    )

    var fovYRadians = PI.toFloat() / 3f
        set(value) {
            field = value
            calculateProjectionMatrix()
        }


    val projection = calculateProjectionMatrix()

    lateinit var gpu: WRWebGPUBinding

    //TODO: have a utility for this sorta stuff, it has repeated itself twice now
    override fun init() {
        gpu = WRWebGPUBinding(surface.webgpu)
    }

    override fun cleanup() {
        gpu.close()
    }
}