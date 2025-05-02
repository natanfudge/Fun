package io.github.natanfudge.fn

import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.webgpu.*
import io.github.natanfudge.fn.window.GlfwComposeWindow
import io.github.natanfudge.fn.window.RepeatingWindowCallbacks
import io.github.natanfudge.fn.window.WindowConfig
import io.github.natanfudge.fn.window.compose
import io.ygdrasil.webgpu.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Semaphore

data class FunWindow(var width: Int, var height: Int)

fun main() {
    val config = WindowConfig(fps = 60, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)

    //TODO: mass cleanups
    val compose = GlfwComposeWindow(show = false)
//    Thread {
        compose.show(config)
//    }.start()

//    var wgpuWindow: WebGPUWindow? = null


    val window = WebGPUWindow(
        init = {
            val device = runBlocking { adapter.requestDevice().getOrThrow() }.ac

            val manager = PipelineManager(
                device, presentationFormat,
                vertexShader = ShaderSource.HotFile("main"),
                hotReloadShaders = HOT_RELOAD_SHADERS
            ).ac

            val window = FunWindow(config.initialWindowWidth, config.initialWindowHeight)

//            val image = readImage(kotlinx.io.files.Path("compose-renders/compose-render-0.png"))

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

//            val confirmedResizeLock = Semaphore(0)

            compose.onFrameReady { bytes, width, height ->
//                if(wgpuWindow!!.open) {
//                    wgpuWindow!!.submitTask {
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
//                    // Show the change in the window as it resizes
//                    if (mismatchingDim) {
//                        println("Got new stuffs")
//                        wgpuWindow!!.frame()
//                    }
//                    }
                    // Draw new frame
//                    confirmedResizeLock.release()
//                }

            }



            return@WebGPUWindow compose.callbacks.compose(object : RepeatingWindowCallbacks {
                override fun AutoClose.frame(delta: Double) {
//                    return
                    manager.poll()

                    //TODO: figure out what to do now, because now it draws with an unfitting texture, and then fixes itself,
                    // but it should still not draw with the unfitting texture but still draw sometimes
//                    if(confirmedResizeLock.availablePermits() == 0)


//                    if (!compose.getFrame { bytes, frameWidth, frameHeight ->
//                            if (bytes != null) {
//                                device.copyExternalImageToTexture(
//                                    source = bytes,
//                                    texture = composeTexture,
//                                    width = window.width, height = window.height
//                                )
//                            }
//                        }) return


                    // Create bind group for the sampler, and texture
                    val bindGroup = device.createBindGroup(
                        BindGroupDescriptor(
                            layout = manager.pipeline.getBindGroupLayout(0u),
                            entries = listOf(
//                                BindGroupEntry(
//                                    binding = 0u,
//                                    resource = BufferBinding(
//                                        buffer = uniformBuffer
//                                    )
//                                ),
                                BindGroupEntry(
                                    binding = 1u,
                                    resource = sampler
                                ),
                                BindGroupEntry(
                                    binding = 2u,
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

                var initialFrame = true

                override fun resize(width: Int, height: Int) {
                    println("Resize start")
                    context.configure(
                        SurfaceConfiguration(
                            device, format = presentationFormat,
                        ),
                        width = width.toUInt(), height = height.toUInt()
                    )
                    window.width = width
                    window.height = height
//                    composeTexture = device.createTexture(
//                        TextureDescriptor(
//                            size = Extent3D(window.width.toUInt(), window.height.toUInt(), 1u),
//                            format = GPUTextureFormat.RGBA8UnormSrgb,
//                            usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst)
//                        )
//                    ).ac
                    println("Resize end")

                    // wait for Compose frame to complete before we draw the next frame
//                    if(!initialFrame) {
//                        confirmedResizeLock.acquire()
//                        wgpuWindow!!.pollTasks()
//                    } else {
//                        initialFrame = false
//                    }

                }
            })


        },
    )
//    wgpuWindow = window
//
//    compose.onResizeComplete {
//        window.submitTask {
//            window.frame()
//        }
//    }


    //TODO: IDK why but reloading seems to only work at the first attempt
    FunHotReload.observation.listen {
        println("Reloadf")
        window.submitTask {
            // Very important to run this on the main thread
            window.restart(config)
            compose.restart()
        }
//        compose.submitRestart(config)
    }


    window.show(config)

    while (true) {
        Thread.sleep(1000)
    }
}