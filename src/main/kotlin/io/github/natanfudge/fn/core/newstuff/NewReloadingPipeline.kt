package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.files.readString
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.ygdrasil.webgpu.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import natan.`fun`.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi


class NewReloadingPipeline(
    label: String, val surface: NewWebGPUSurface, val vertexSource: ShaderSource, val fragmentSource: ShaderSource = vertexSource,
    val pipelineConfigBuilder: NewWebGPUContext.(vertex: GPUShaderModule, fragment: GPUShaderModule) -> GPURenderPipelineDescriptor,
) : NewFun("ReloadingPipeline-$label", surface) {
    private var _pipeline: GPURenderPipeline? by memo { null}
    val pipeline: GPURenderPipeline get() = _pipeline!!
    private var vertexShader: GPUShaderModule? by memo { null }
    private var fragmentShader: GPUShaderModule? by memo { null }

    val pipelineLoaded by event<GPURenderPipeline>()

    private fun closePipeline() {
        _pipeline?.close()
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
        this._pipeline = ctx.device.createRenderPipeline(pipelineConfigBuilder(ctx, vertexShader!!, fragmentShader!!))
        pipelineLoaded(_pipeline!!)
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
                val ctx = surface.webgpu
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
