package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.files.readString
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.ygdrasil.webgpu.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import natan.`fun`.generated.resources.Res
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.resources.ExperimentalResourceApi



class NewReloadingPipeline(
    label: String, val surface: NewWebGPUSurface, val vertexSource: ShaderSource, val fragmentSource: ShaderSource = vertexSource,
    val pipelineConfigBuilder: NewWebGPUContext.(vertex: GPUShaderModule, fragment: GPUShaderModule) -> GPURenderPipelineDescriptor,
) : NewFun("ReloadingPipeline-$label", surface) {
    lateinit var pipeline: GPURenderPipeline
    private var vertexShader: GPUShaderModule? = null
    private var fragmentShader: GPUShaderModule? = null

    val pipelineLoaded by event<GPURenderPipeline>()

    private fun closePipeline() {
        if (::pipeline.isInitialized) {
            pipeline.close()
        }
        vertexShader?.close()
        fragmentShader?.close()
    }

    private fun loadPipeline() {
        closePipeline()
        val (vertex, fragment) = runBlocking {
            if (vertexSource == fragmentSource) {
                val shader = loadShader(vertexSource)
                shader to shader
            } else {
                loadShader(vertexSource) to loadShader(fragmentSource)
            }
        }

        val ctx = surface.webgpu
        this.vertexShader = ctx.device.createShaderModule(ShaderModuleDescriptor(code = vertex))
        this.fragmentShader = ctx.device.createShaderModule(ShaderModuleDescriptor(code = fragment))
        this.pipeline = ctx.device.createRenderPipeline(pipelineConfigBuilder(ctx, vertexShader!!, fragmentShader!!))
        pipelineLoaded(pipeline)
    }

    override fun init() {
        if (HOT_RELOAD_SHADERS) {
            reloadOnChange(vertexSource)
            if (vertexSource != fragmentSource) {
                reloadOnChange(fragmentSource)
            }
        }
        loadPipeline()
    }

    override fun cleanup() {
        closePipeline()
    }

    private fun reloadOnChange(
        shaderSource: ShaderSource,
    ) {
        if (shaderSource is ShaderSource.HotFile) {
            println("Re-registering callback")
            context.fsWatcher.onFileChanged(shaderSource.getSourceFile()) {
                // Small hack to see if the new shader source is valid - try to compile it and see if it fails
                val ctx =  surface.webgpu
                ctx.device.pushErrorScope(GPUErrorFilter.Validation)

                val (module, error) = runBlocking {
                    ctx.device.createShaderModule(ShaderModuleDescriptor(loadShader(shaderSource))) to
                            ctx.device.popErrorScope().getOrThrow()
                }

                module.close()
                if (error == null) {
                    loadPipeline()
                } else {
                    println("Failed to compile new shader for reload: $error")
                }
            }.closeWithThis()
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
suspend fun loadShader(source: ShaderSource): String {
    val code = when (source) {
        // Load directly from source when hot reloading
        is ShaderSource.HotFile -> if (HOT_RELOAD_SHADERS) {
            Thread.sleep(100) //TODO: seems sus
            val file = source.getSourceFile()

            println("Loading shader at '${file}'")
            file.readString()
        } else Res.readBytes(source.fullPath()).decodeToString()

        is ShaderSource.RawString -> source.shader
    }
    return code
}


private fun ShaderSource.HotFile.fullPath() = "files/shaders/${path}.wgsl"

// HACK: this might not work always
private fun ShaderSource.HotFile.getSourceFile() = Path("src/main/composeResources/", fullPath())
