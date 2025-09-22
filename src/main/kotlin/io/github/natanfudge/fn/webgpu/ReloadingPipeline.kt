package io.github.natanfudge.fn.webgpu

import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.files.readString
import io.github.natanfudge.fn.util.EventEmitter
import io.ygdrasil.webgpu.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import natan.`fun`.generated.resources.Res
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.reload.core.Either
import org.jetbrains.compose.reload.core.Left
import org.jetbrains.compose.reload.core.Right
import org.jetbrains.compose.resources.ExperimentalResourceApi

sealed interface ShaderSource {
    data class RawString(@param:Language("WGSL") val shader: String, val name: String = "Main") : ShaderSource

    /**
     * Will be fetched from `files/shaders/${path}.wgsl`
     * If [ReloadingPipeline.hotReloadShaders] is true, changes to this file will be auto-reloaded.
     */
    data class HotFile(val path: String) : ShaderSource
}


var nextPipelineIndex = 0


class ActivePipeline(
    val ctx: WebGPUContext,
    val vertexSource: ShaderSource,
    val fragmentSource: ShaderSource,
    val pipelineDescriptorBuilder: PipelineDescriptorBuilder,
    val pipelineLoaded: EventEmitter<GPURenderPipeline>,
) : InvalidationKey() {
    var pipeline: GPURenderPipeline? = null
    private var vertexShader: GPUShaderModule? = null
    private var fragmentShader: GPUShaderModule? = null


    fun reload() {
        check(valid)
        check(ctx.valid)
        val (vertex, fragment) = runBlocking {
            if (vertexSource == fragmentSource) {
                val shader = loadShader(vertexSource)
                shader to shader
            } else {
                loadShader(vertexSource) to loadShader(fragmentSource)
            }
        }

        val vertexShader = safeCreateShaderModule(vertex)
        val fragmentShader = safeCreateShaderModule(fragment)
        if (vertexShader is Left && fragmentShader is Left) {
            // Success: reload pipeline
            close()
            this.vertexShader = vertexShader.value
            this.fragmentShader = fragmentShader.value
            this.pipeline = ctx.device.createRenderPipeline(pipelineDescriptorBuilder(ctx, this.vertexShader!!, this.fragmentShader!!))
            pipelineLoaded(this.pipeline!!)
        } else {
            // Fail - do nothing
            if (vertexShader is Right) {
                logError("Shaders", null) { "Error while compiling vertex shader: ${vertexShader.value}" }
            }
            if (fragmentShader is Right && (vertexSource != fragmentSource || vertexShader is Left)) {
                logError("Shaders", null) { "Error while compiling fragment shader: ${fragmentShader.value}" }
            }

            // TO DO: have some sort of plan B in case it fails in first initialization, because we won't have any existing shader code to reuse.
//            this.vertexShader = null
//            this.fragmentShader = null
//            this.pipeline = null
        }
    }

    init {
        reload()
    }

    override fun close() {
        // This might get called multiple times so we make sure not to overdo it
        pipeline?.close()
        pipeline = null
        vertexShader?.close()
        vertexShader = null
        fragmentShader?.close()
        fragmentShader = null
    }

    private fun safeCreateShaderModule(code: String): Either<GPUShaderModule, GPUError> {
        // Small hack to see if the new shader source is valid - try to compile it and see if it fails
        ctx.device.pushErrorScope(GPUErrorFilter.Validation)
        val module = ctx.device.createShaderModule(ShaderModuleDescriptor(code))
        val error = runBlocking { ctx.device.popErrorScope().getOrThrow() }

        if (error == null) {
            return Left(module)
        } else {
            module.close()
            return Right(error)
        }
    }
}

typealias PipelineDescriptorBuilder = WebGPUContext.(vertex: GPUShaderModule, fragment: GPUShaderModule) -> GPURenderPipelineDescriptor

class ReloadingPipeline(
    label: String, val surface: WebGPUSurfaceHolder, val vertexSource: ShaderSource, val fragmentSource: ShaderSource = vertexSource,
    val pipelineDescriptorBuilder: PipelineDescriptorBuilder,
) : Fun("ReloadingPipeline-$label") {
    val pipelineLoaded by event<GPURenderPipeline>()

    // NOTE: we don't want to depend on the WebGPUSurface directly because it is actually recreated every refresh, in contrast with the window that
    // is recreated when the window is actually recreated. This is kind of confusing and I would like to do something better.
    val active by cached(surface.windowHolder.window) {
        ActivePipeline(surface.surface, vertexSource, fragmentSource, pipelineDescriptorBuilder, pipelineLoaded)
    }

    val valid get() = active.valid

    val pipeline: GPURenderPipeline get() = active.pipeline!!


    val index = nextPipelineIndex++

    init {
        if (HOT_RELOAD_SHADERS) {
            reloadOnChange(vertexSource)
            if (vertexSource != fragmentSource) {
                reloadOnChange(fragmentSource)
            }
        }
    }


    private fun reloadOnChange(
        shaderSource: ShaderSource,
    ) {
        if (shaderSource is ShaderSource.HotFile) {
            check(active.valid)
            fsWatcher.onFileChanged(shaderSource.getSourceFile()) {
                active.reload()
            }.closeWithThis()
        }
    }
}


@OptIn(ExperimentalResourceApi::class)
suspend fun loadShader(source: ShaderSource): String {
    val code = when (source) {
        // Load directly from source when hot reloading
        is ShaderSource.HotFile -> if (HOT_RELOAD_SHADERS) {
            val file = source.getSourceFile()
            SimpleLogger.info("Shaders") { "Loading shader at '${file}'" }
            file.readString()
        } else Res.readBytes(source.fullPath()).decodeToString()

        is ShaderSource.RawString -> source.shader
    }
    return code
}


private fun ShaderSource.HotFile.fullPath() = "files/shaders/${path}.wgsl"

// HACK: this might not work always
private fun ShaderSource.HotFile.getSourceFile() = Path("src/main/composeResources/", fullPath())

