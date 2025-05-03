package io.github.natanfudge.fn.compose

import androidx.compose.runtime.Composable
import io.github.natanfudge.fn.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.webgpu.AutoClose
import io.github.natanfudge.fn.webgpu.ManagedPipeline
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.github.natanfudge.fn.webgpu.copyExternalImageToTexture
import io.github.natanfudge.fn.window.GlfwComposeWindow
import io.github.natanfudge.fn.window.WindowConfig
import io.ygdrasil.webgpu.*

class ComposeWebGPURenderer(private val config: WindowConfig, content: @Composable () -> Unit) {
    private val compose = GlfwComposeWindow(content, show = false)

    init {
        compose.show(config)
    }

    var textureWidth = config.initialWindowWidth
    var textureHeight = config.initialWindowHeight
    lateinit var composeTexture: GPUTexture
    lateinit var sampler: GPUSampler
    lateinit var fullscreenQuad: ManagedPipeline

    /**
     * Should be called during webgpu initialization
     */
    fun init(device: GPUDevice, ac: AutoClose, fsWatcher: FileSystemWatcher, presentationFormat: GPUTextureFormat) = with(ac) {
        fullscreenQuad = ManagedPipeline(
            device, fsWatcher, presentationFormat,
            vertexShader = ShaderSource.HotFile("fullscreen_quad.vertex"),
            fragmentShader = ShaderSource.HotFile("fullscreen_quad.fragment"),
            hotReloadShaders = HOT_RELOAD_SHADERS
        ).ac

        //TODO: with better "lifecycle integration" this would look better...
        composeTexture = device.createTexture(
            TextureDescriptor(
                size = Extent3D(textureWidth.toUInt(), textureHeight.toUInt(), 1u),
                format = GPUTextureFormat.RGBA8UnormSrgb,
                usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
            )
        ).ac

        sampler = device.createSampler(
            SamplerDescriptor(
                magFilter = GPUFilterMode.Linear,
                minFilter = GPUFilterMode.Linear
            )
        ).ac

        compose.onFrameReady { bytes, width, height ->
            val mismatchingDim = width != textureWidth || height != textureHeight
            if (mismatchingDim) {
                textureWidth = width
                textureHeight = height
                composeTexture = device.createTexture(
                    TextureDescriptor(
                        size = Extent3D(textureWidth.toUInt(), textureHeight.toUInt(), 1u),
                        format = GPUTextureFormat.RGBA8UnormSrgb,
                        usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
                    )
                ).ac
            }
            device.copyExternalImageToTexture(
                source = bytes,
                texture = composeTexture,
                width = width, height = height
            )
        }
    }

    /**
     * Should be called every frame to draw Compose content
     */
    fun frame(device: GPUDevice, ac: AutoClose, pass: GPURenderPassEncoder)  = with(ac){
        // Create bind group for the sampler, and texture
        val bindGroup = device.createBindGroup(
            BindGroupDescriptor(
                layout = fullscreenQuad.pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(
                        binding = 0u,
                        resource = sampler
                    ),
                    BindGroupEntry(
                        binding = 1u,
                        resource = composeTexture.createView()
                    )
                )
            )
        ).ac

        pass.setPipeline(fullscreenQuad.pipeline)
        pass.setBindGroup(0u, bindGroup)
        pass.draw(6u)
        pass.end()
    }

    /**
     * These callbacks should be called when these events occur to let Compose know what is happening
     */
    val callbacks = compose.callbacks

    fun restart(config: WindowConfig = WindowConfig()) = compose.restart(config)
}