package io.github.natanfudge.fn

import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.GlfwComposeWindow
import io.github.natanfudge.fn.window.RepeatingWindowCallbacks
import io.github.natanfudge.fn.window.WindowConfig
import io.github.natanfudge.fn.window.compose
import io.ygdrasil.webgpu.*
import kotlinx.coroutines.runBlocking

data class FunWindow(var width: Int, var height: Int)

fun main() {
    val config = WindowConfig(fps = 60, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)

    val compose = GlfwComposeWindow(show = false)
    compose.show(config)

    val window = WebGPUWindow(
        init = {
            val device = runBlocking { adapter.requestDevice().getOrThrow() }.ac

            val manager = ManagedPipeline(
                device, presentationFormat,
                vertexShader = ShaderSource.HotFile("fullscreen_quad.vertex"),
                fragmentShader = ShaderSource.HotFile("fullscreen_quad.fragment"),
                hotReloadShaders = HOT_RELOAD_SHADERS
            ).ac

            val window = FunWindow(config.initialWindowWidth, config.initialWindowHeight)

            var textureWidth = window.width
            var textureHeight = window.height
            var composeTexture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(textureWidth.toUInt(), textureHeight.toUInt(), 1u),
                    format = GPUTextureFormat.RGBA8UnormSrgb,
                    usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
                )
            ).ac


            // Create a sampler
            val sampler = device.createSampler(
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



            return@WebGPUWindow compose.callbacks.compose(object : RepeatingWindowCallbacks {
                override fun AutoClose.frame(delta: Double) {
                    manager.poll()

                    // Create bind group for the sampler, and texture
                    val bindGroup = device.createBindGroup(
                        BindGroupDescriptor(
                            layout = manager.pipeline.getBindGroupLayout(0u),
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

                    val commandEncoder = device.createCommandEncoder().ac
                    val textureView = context.getCurrentTexture().texture.createView().ac
                    val renderPassDescriptor = RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = textureView,
                                clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                loadOp = GPULoadOp.Clear,
                                storeOp = GPUStoreOp.Store
                            )
                        )
                    )

                    val passEncoder = commandEncoder.beginRenderPass(renderPassDescriptor)
                    passEncoder.setPipeline(manager.pipeline)
                    passEncoder.setBindGroup(0u, bindGroup)
                    passEncoder.draw(6u)
                    passEncoder.end()

                    device.queue.submit(listOf(commandEncoder.finish()))

                    context.present()
                }


                override fun resize(width: Int, height: Int) {
                    context.configure(
                        SurfaceConfiguration(
                            device, format = presentationFormat,
                        ),
                        width = width.toUInt(), height = height.toUInt()
                    )
                    window.width = width
                    window.height = height
                }
            })


        },
    )


    //TODO: IDK why but reloading seems to only work at the first attempt
    FunHotReload.observation.listen {
        println("Reloadf")
        window.submitTask {
            // Very important to run this on the main thread
            window.restart(config)
            compose.restart()
        }
    }


    window.show(config)

    while (true) {
        Thread.sleep(1000)
    }
}