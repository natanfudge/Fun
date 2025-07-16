package io.github.natanfudge.fn.webgpu

import io.github.natanfudge.fn.core.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.files.readString
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.util.closeAll
import io.ygdrasil.webgpu.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import natan.`fun`.generated.resources.Res
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.resources.ExperimentalResourceApi

sealed interface ShaderSource {
    data class RawString(@param:Language("WGSL") val shader: String, val name: String = "Main") : ShaderSource

    /**
     * Will be fetched from `files/shaders/${path}.wgsl`
     * If [ReloadingPipelineOld.hotReloadShaders] is true, changes to this file will be auto-reloaded.
     */
    data class HotFile(val path: String) : ShaderSource
}

class ReloadingPipelineOld(
    val vertexShader: GPUShaderModule,
    val fragmentShader: GPUShaderModule,
    val pipeline: GPURenderPipeline,
    // On the other hand when the surface changes ctx does change and then you get old values for the children of this
) : AutoCloseable {
    companion object {
        inline fun build(
            vertexShaderCode: String,
            fragmentShaderCode: String,
            descriptorBuilder: (GPUShaderModule, GPUShaderModule) -> GPURenderPipelineDescriptor,
            ctx: WebGPUContext,
        ): ReloadingPipelineOld {
            val vertexShader = ctx.device.createShaderModule(ShaderModuleDescriptor(code = vertexShaderCode))
            val fragmentShader = ctx.device.createShaderModule(ShaderModuleDescriptor(code = fragmentShaderCode))
            val pipeline = ctx.device.createRenderPipeline(descriptorBuilder(vertexShader, fragmentShader))
            return ReloadingPipelineOld(vertexShader, fragmentShader, pipeline)
        }
    }

    override fun toString(): String {
        return "Reloading Pipeline"
    }


    override fun close() {
        println("CLosing pipeline ${pipeline.label}")
        closeAll(vertexShader, fragmentShader, pipeline)
    }
}


inline fun createReloadingPipeline(
    label: String,
    surfaceLifecycle: Lifecycle<*, WebGPUContext>,
    fsWatcher: FileSystemWatcher,
    vertexShader: ShaderSource,
    fragmentShader: ShaderSource = vertexShader,
    crossinline descriptorBuilder: WebGPUContext.(GPUShaderModule, GPUShaderModule) -> GPURenderPipelineDescriptor,
): Lifecycle<WebGPUContext, ReloadingPipelineOld> {
    val lifecycle = surfaceLifecycle.bind("Reloading Pipeline of $label") {
        // SLOW: this should prob not be blocking like this
        val (vertex, fragment) = runBlocking {
            if (vertexShader == fragmentShader) {
                val shader = loadShader(vertexShader)
                shader to shader
            } else {
                loadShader(vertexShader) to loadShader(fragmentShader)
            }
        }

        ReloadingPipelineOld.build(
            vertexShaderCode = vertex,
            fragmentShaderCode = fragment,
            { v, f -> it.descriptorBuilder(v, f) }, it
        )
    }

    if (HOT_RELOAD_SHADERS) {
        reloadOnChange(vertexShader, fsWatcher, surfaceLifecycle, lifecycle)
        if (fragmentShader != vertexShader) {
            reloadOnChange(fragmentShader, fsWatcher, surfaceLifecycle, lifecycle)
        }
    }

    return lifecycle
}


fun reloadOnChange(
    shaderSource: ShaderSource,
    fsWatcher: FileSystemWatcher,
    surfaceLifecycle: Lifecycle<*, WebGPUContext>,
    pipelineLifecycle: Lifecycle<*, *>,
) {
    if (shaderSource is ShaderSource.HotFile) {
        println("Re-registering callback")
        fsWatcher.onFileChanged(shaderSource.getSourceFile()) {
            // Small hack to see if the new shader source is valid - try to compile it and see if it fails
            val surface = surfaceLifecycle.assertValue
            surface.device.pushErrorScope(GPUErrorFilter.Validation)

            val (module, error) = runBlocking {
                surfaceLifecycle.assertValue.device.createShaderModule(ShaderModuleDescriptor(loadShader(shaderSource))) to
                        surface.device.popErrorScope().getOrThrow()
            }

            module.close()
            if (error == null) {
                pipelineLifecycle.restart()
            } else {
                println("Failed to compile new shader for reload: $error")
            }
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
