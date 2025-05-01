package io.github.natanfudge.fn.webgpu

import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.window.RepeatingWindowCallbacks
import io.github.natanfudge.fn.window.WindowConfig
import io.ygdrasil.webgpu.*
import kotlinx.coroutines.runBlocking

//TODO: next step: try to open another window to render the compose stuff in it

const val HOT_RELOAD_SHADERS = true

fun main() {
    val config = WindowConfig(fps = 60, initialTitle = "Fun", initialWindowWidth = 800, initialWindowHeight = 600)
    val window = WebGPUWindow(
        init = {
            val device = runBlocking { adapter.requestDevice().getOrThrow() }.ac

            val manager = PipelineManager(
                device, presentationFormat,
                vertexShader = ShaderSource.HotFile("triangle"),
                hotReloadShaders = HOT_RELOAD_SHADERS
            ).ac

            return@WebGPUWindow object: RepeatingWindowCallbacks {
                override fun AutoClose.frame(delta: Double) {
                    manager.poll()

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
                    passEncoder.draw(3u)
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
                }
            }
        },
    )

    FunHotReload.observation.listen {
        println("Reloading")
        window.submitTask {
            // Very important to run this on the main thread
            window.restart()
        }
    }


    window.show(config)
}