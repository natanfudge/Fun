@file:OptIn(InternalComposeUiApi::class)

package io.github.natanfudge.fn.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.core.InvalidationKey
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.valid
import io.github.natanfudge.fn.render.WorldRenderer
import io.github.natanfudge.fn.render.toExtent3D
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.*
import io.ygdrasil.webgpu.*
import org.lwjgl.glfw.GLFW.*

private var samplerIndex = 0

internal class ComposeWebgpuSurface(val ctx: WebGPUContext, val context: FunContext) : InvalidationKey() {
    val myIndex = samplerIndex++
    val sampler = ctx.device.createSampler(
        SamplerDescriptor(
            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear,
            label = "Compose Sampler #$myIndex"
        )
    )

    override fun close() {
        closeAll(sampler)
    }

    override fun toString(): String {
        return "Compose WebGPU Surface #$myIndex"
    }
}

private var textureIndex = 0

internal class ComposeTexture(val ctx: WebGPUContext, val size: IntSize) : InvalidationKey() {
    val myIndex = textureIndex++
    val composeTexture = ctx.device.createTexture(
        TextureDescriptor(
            size = size.toExtent3D(),
            format = GPUTextureFormat.RGBA8UnormSrgb,
            usage = setOf(GPUTextureUsage.TextureBinding, GPUTextureUsage.RenderAttachment, GPUTextureUsage.CopyDst),
            label = toString()
        )
    )

    override fun toString(): String {
        return "Compose Texture #$myIndex $size"
    }


    override fun close() {
        closeAll(composeTexture)
    }
}

val glfwTextCursor = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR)           // I-beam
val glfwCrossHairCursor = glfwCreateStandardCursor(GLFW_CROSSHAIR_CURSOR)     // Crosshair
val glfwHandCursor = glfwCreateStandardCursor(GLFW_HAND_CURSOR)

 class ComposeHudWebGPURenderer(
    worldRenderer: WorldRenderer,
    onCreateScene: (GlfwComposeScene) -> Unit,
    show: Boolean = false,
) : Fun("ComposeHudWebGPURenderer") {
    private val webGPUHolder = worldRenderer.surfaceHolder

    val offscreenComposeRenderer: ComposeOpenGLRenderer = ComposeOpenGLRenderer(
        webGPUHolder.windowHolder.params,
        show = show, name = "HUD", onSetPointerIcon = {
            setHostWindowCursorIcon(it)
        },
        onFrame = { (bytes, size) ->
            check(!closed)
            check(texture.valid)
            webGPUHolder.surface.device.copyExternalImageToTexture(
                source = bytes,
                texture = texture.composeTexture,
                width = size.width, height = size.height
            )
        },
        onCreateScene = onCreateScene
    )

    fun setContent(content: @Composable () -> Unit) {
        offscreenComposeRenderer.scene.setContent(content)
    }

    private val surface by cached(webGPUHolder.surface) {
        ComposeWebgpuSurface(webGPUHolder.surface, context)
    }

    private var texture by cached(webGPUHolder.surface) {
        // Need a new compose frame when the texture is recreated
        offscreenComposeRenderer.scene.frameInvalid = true
        ComposeTexture(webGPUHolder.surface, webGPUHolder.size)
    }

    val shader = ReloadingPipeline(
        "Compose Fullscreen Quad",
        webGPUHolder,
        vertexSource = ShaderSource.HotFile("compose/fullscreen_quad.vertex"),
        fragmentSource = ShaderSource.HotFile("compose/fullscreen_quad.fragment"),
    ) { vertex, fragment ->

        RenderPipelineDescriptor(
            layout = null,
            vertex = VertexState(
                module = vertex,
                entryPoint = "vs_main"
            ),
            fragment = FragmentState(
                module = fragment,
                // Allow transparency
                targets = listOf(
                    ColorTargetState(
                        format = presentationFormat,
                        // Straight‑alpha blending:  out = src.rgb·src.a  +  dst.rgb·(1‑src.a)
                        blend = BlendState(
                            color = BlendComponent(
                                srcFactor = GPUBlendFactor.SrcAlpha,
                                dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                                operation = GPUBlendOperation.Add
                            ),
                            alpha = BlendComponent(
                                srcFactor = GPUBlendFactor.One,
                                dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                                operation = GPUBlendOperation.Add
                            )
                        ),
                        writeMask = setOf(GPUColorWrite.All)
                    )
                ),
                entryPoint = "fs_main",
            ),
            primitive = PrimitiveState(
                topology = GPUPrimitiveTopology.TriangleList
            ),
            label = "Compose HUD Pipeline",
        )
    }
    private var bindGroup by cached(texture) {
        println("Create CHUD bindgroup")
        ComposeBindGroup(shader.pipeline, texture, surface)
    }


    init {
        // For world input events, we need to ray trace to gui boxes, take the (x,y) on that surface, and pipe that (x,y) to the surface.
        context.events.input.listen { input ->
            offscreenComposeRenderer.scene.sendInputEvent(input)
        }
        context.events.densityChange.listen { (newDensity) ->
            val scene = offscreenComposeRenderer.scene
            if (scene.focused) {
                scene.scene.density = newDensity
            }
        }

        events.windowResize.listen {
            offscreenComposeRenderer.resize(it.size)
            texture = ComposeTexture(webGPUHolder.surface, webGPUHolder.size)
            bindGroup = ComposeBindGroup(shader.pipeline, texture, surface)
        }
        shader.pipelineLoaded.listen {
            bindGroup = ComposeBindGroup(shader.pipeline, texture, surface)
        }
        worldRenderer.beforeSubmitDraw.listen { (encoder, drawTarget) ->
            check(shader.valid)
            check(bindGroup.valid)
            val renderPassDescriptor = RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = drawTarget,
                        clearValue = Color(0.0, 0.0, 0.0, 0.0),
                        loadOp = GPULoadOp.Load, // Keep the previous content, we want to overlay on top of it
                        storeOp = GPUStoreOp.Store
                    ),
                ),
            )

            // Create bind group for the sampler, and texture
            val pass = encoder.beginRenderPass(renderPassDescriptor)
//            println("Before hud render")
            pass.setPipeline(shader.pipeline)
            pass.setBindGroup(0u, bindGroup.group)
            pass.draw(6u)
            pass.end()
//            println("After hud render")
        }
    }

    private fun setHostWindowCursorIcon(icon: PointerIcon) {
        val cursor: Long = when (icon) {
            PointerIcon.Default -> 0L   // Arrow
            PointerIcon.Text -> glfwTextCursor
            PointerIcon.Crosshair -> glfwCrossHairCursor
            PointerIcon.Hand -> glfwHandCursor
            else -> 0L
        }

        glfwSetCursor(webGPUHolder.windowHolder.handle, cursor)
    }
}

internal class ComposeBindGroup(pipeline: GPURenderPipeline, texture: ComposeTexture, surface: ComposeWebgpuSurface) : InvalidationKey() {
    val resource = texture.composeTexture.createView()
    val group = texture.ctx.device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = listOf(
                BindGroupEntry(
                    binding = 0u,
                    resource = surface.sampler
                ),

                BindGroupEntry(
                    binding = 1u,
                    resource = resource
                )

            )
        )
    )

    override fun close() {
        closeAll(resource, group)
    }
}