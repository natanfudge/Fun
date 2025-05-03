package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.FunWindow
import io.github.natanfudge.fn.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.compose.ComposeWebGPURenderer
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.webgpu.AutoClose
import io.github.natanfudge.fn.webgpu.ManagedPipeline
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.github.natanfudge.fn.webgpu.WebGPUWindow
import io.github.natanfudge.fn.window.RepeatingWindowCallbacks
import io.github.natanfudge.fn.window.WindowConfig
import io.github.natanfudge.fn.window.compose
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.SurfaceConfiguration
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

//TODO:
// 3. Replace FunWindow with storing width/height in the FunGlfwWindow and accessing it in the frame
// 5. keep in the back of the mind to do a LifeCycle system
// 6. Start drawing basic objects:
//   A. An origin marker
//   B. XYZ axis arrows
// 7. Figure out how to draw spheres
// 8. Draw a sphere for the origin marker and the arrows
// 9. Implement WASD camera
// 10. Implement orbital camera
// 11. Integrate the switch between both cameras
// 12. Material rendering system:
//      A. Start by having only a mesh
//      B. Then add texturing
//      C. Then add a normal map
//      D. then add phong lighting
//      E. then add the other PBR things
// 13. Ray casting & selection
// 14. Physics
// 15. Trying making a basic game
// 16. Integrate the Fun "ECS" system and allow assigning physicality to components, allowing you to click on things and view their state
fun WebGPUContext.funRender(window: WebGPUWindow, config: WindowConfig, compose: ComposeWebGPURenderer): RepeatingWindowCallbacks {
    val device = runBlocking { adapter.requestDevice().getOrThrow() }.ac

    val fsWatcher = FileSystemWatcher()

    val triangle = ManagedPipeline(device, fsWatcher, presentationFormat, vertexShader = ShaderSource.HotFile("triangle")).ac

    val windowDim = FunWindow(config.initialWindowWidth, config.initialWindowHeight)

    compose.init(device,this,fsWatcher,presentationFormat)


    return compose.callbacks.compose(object : RepeatingWindowCallbacks {
        override fun AutoClose.frame(deltaMs: Double) {
            // Interestingly, this call (context.getCurrentTexture()) invokes VSync (so it stalls here usually)
            val windowFrame = context.getCurrentTexture()
                .also { it.texture.ac } // Close texture

            val normalFrameTimeMs = (1f / window.refreshRate) * 1000
            // It's not exact, but if it's almost twice as long (or more), it means we took too much time to make a frame
            if (deltaMs > normalFrameTimeMs * 1.8f) {
                val missingFrames = (deltaMs / normalFrameTimeMs).roundToInt() - 1
                val plural = missingFrames > 1
                println("Frame delayed, took ${deltaMs}ms to make a frame instead of the usual ${normalFrameTimeMs}ms," +
                        " so about $missingFrames ${if(plural) "frames were" else "frame was"} skipped")
            }

            if (HOT_RELOAD_SHADERS) {
                fsWatcher.poll()
            }
            val commandEncoder = device.createCommandEncoder().ac

            val textureView = windowFrame.texture.createView().ac
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


            val pass = commandEncoder.beginRenderPass(renderPassDescriptor)
            pass.setPipeline(triangle.pipeline)
            pass.draw(3u)

            // Draw Compose UI
            compose.frame(device,this, pass)

            device.queue.submit(listOf(commandEncoder.finish()))

            context.present()
        }


        //TODO: this should be completely handled by the window system
        override fun resize(width: Int, height: Int) {
            context.configure(
                SurfaceConfiguration(
                    device, format = presentationFormat
                ),
                width = width.toUInt(), height = height.toUInt()
            )
            windowDim.width = width
            windowDim.height = height
        }
    })
}