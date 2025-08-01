@file:OptIn(InternalComposeUiApi::class)

package io.github.natanfudge.fn.core.newstuff

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.compose.GlfwComposeScene
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.*
import io.ygdrasil.webgpu.*
import org.lwjgl.glfw.GLFW.*

private var samplerIndex = 0

internal class NewComposeWebgpuSurface(val ctx: NewWebGPUContext, val context: NewFunContext, val composeScene: GlfwComposeScene) : InvalidationKey() {
    // TODO: these listeners should not be here and i will def need to move them for panel input
    // For world input events, we need to ray trace to gui boxes, take the (x,y) on that surface, and pipe that (x,y) to the surface.
    private val inputListener = context.events.input.listenUnscoped { input ->
//        val window = composeWindowLifecycle.value ?: return@listenUnscoped
        composeScene.sendInputEvent(input)
    }
    private val densityListener = context.events.densityChange.listenUnscoped { (newDensity) ->
//        val window = composeWindowLifecycle.value ?: return@listenUnscoped
        if (composeScene.focused) {
            composeScene.scene.density = newDensity
        }
    }

    val myIndex = samplerIndex++
    val sampler = ctx.device.createSampler(
        SamplerDescriptor(
            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear,
            label = "Compose Sampler #$myIndex"
        )
    )

    override fun close() {
        closeAll(sampler, inputListener, densityListener)
    }

    override fun toString(): String {
        return "Compose WebGPU Surface #$myIndex"
    }
}

private var textureIndex = 0

internal class NewComposeTexture(val ctx: NewWebGPUContext, val size: IntSize) : AutoCloseable {
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

internal class NewComposeHudWebGPURenderer(
    worldRenderer: NewWorldRenderer,
    show: Boolean = false,
) : NewFun("ComposeHudWebGPURenderer") {
    private val webGPUHolder = worldRenderer.surfaceHolder


    val offscreenComposeRenderer: NewComposeOpenGLRenderer = NewComposeOpenGLRenderer(
        webGPUHolder.windowHolder.params,
        show = show, name = "Compose", onSetPointerIcon = {
            setHostWindowCursorIcon(it)
        },
        onFrame = { (bytes, size) ->
            webGPUHolder.surface.device.copyExternalImageToTexture(
                source = bytes,
                texture = texture.composeTexture,
                width = size.width, height = size.height
            )
        }
    )

    fun setContent(content: @Composable () -> Unit) {
        offscreenComposeRenderer.scene.setContent(content)
    }

    val surface by cached(webGPUHolder.surface) {
        NewComposeWebgpuSurface(webGPUHolder.surface, context, offscreenComposeRenderer.scene)
    }

    var texture by cached(webGPUHolder.surface) {
        // Need a new compose frame when the texture is recreated
        offscreenComposeRenderer.scene.frameInvalid = true
        NewComposeTexture(webGPUHolder.surface, webGPUHolder.size)
    }
    // TODO: crash in skiko on refresh [NewComposeBindGroup, NewComposeHudWebGPURenderer] after resizing window

    val shader = NewReloadingPipeline(
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
    var bindGroup by cached(surface) {
        NewComposeBindGroup(shader.pipeline, texture, surface)
    }


    init {
        events.windowResize.listen {
            offscreenComposeRenderer.resize(it.size)
            texture = NewComposeTexture(webGPUHolder.surface, webGPUHolder.size)
            bindGroup = NewComposeBindGroup(shader.pipeline, texture, surface)
        }
        shader.pipelineLoaded.listen {
            bindGroup = NewComposeBindGroup(shader.pipeline, texture, surface)
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
            pass.setPipeline(shader.pipeline)
            pass.setBindGroup(0u, bindGroup.group)
            pass.draw(6u)
            pass.end()
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

internal class NewComposeBindGroup(pipeline: GPURenderPipeline, texture: NewComposeTexture, surface: NewComposeWebgpuSurface) : InvalidationKey() {
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