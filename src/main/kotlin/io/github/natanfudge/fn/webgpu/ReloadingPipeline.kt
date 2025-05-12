package io.github.natanfudge.fn.webgpu

import io.github.natanfudge.fn.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.files.readString
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.util.closeAll
import io.ygdrasil.webgpu.GPUErrorFilter
import io.ygdrasil.webgpu.GPURenderPipelineDescriptor
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import natan.`fun`.generated.resources.Res
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.resources.ExperimentalResourceApi

sealed interface ShaderSource {
    data class RawString(@Language("WGSL") val shader: String, val name: String = "Main") : ShaderSource

    /**
     * Will be fetched from `files/shaders/${path}.wgsl`
     * If [ReloadingPipeline.hotReloadShaders] is true, changes to this file will be auto-reloaded.
     */
    data class HotFile(val path: String) : ShaderSource
}

class ReloadingPipeline(
    val vertexShaderCode: String,
    val fragmentShaderCode: String,
    descriptorBuilder: (GPUShaderModule, GPUShaderModule) -> GPURenderPipelineDescriptor,
    val ctx: WebGPUContext,
    // On the other hand when the surface changes ctx does change and then you get old values for the children of this
) : AutoCloseable {
    override fun toString(): String {
        return "Reloading Pipeline with ${vertexShaderCode.length} chars of vertex shader and ${fragmentShaderCode.length} chars of fragment shader"
    }

    val vertexShader = ctx.device.createShaderModule(ShaderModuleDescriptor(code = vertexShaderCode))

    val fragmentShader = ctx.device.createShaderModule(ShaderModuleDescriptor(code = fragmentShaderCode))



    val pipeline = ctx.device.createRenderPipeline(descriptorBuilder(vertexShader, fragmentShader))

    override fun close() {
        closeAll(vertexShader, fragmentShader, pipeline)
    }
}

fun createReloadingPipeline(
    label: String,
    surfaceLifecycle: Lifecycle<*, WebGPUContext>,
    fsWatcher: FileSystemWatcher,
    vertexShader: ShaderSource,
    fragmentShader: ShaderSource = vertexShader,
    descriptorBuilder: WebGPUContext.(GPUShaderModule, GPUShaderModule) -> GPURenderPipelineDescriptor,
): Lifecycle<WebGPUContext, ReloadingPipeline> {
    val lifecycle = surfaceLifecycle.bind("Reloading Pipeline of $label") {
        // SLOW: this should prob not be blocking like this
        runBlocking {
            ReloadingPipeline(
                vertexShaderCode = loadShader(vertexShader),
                fragmentShaderCode = loadShader(fragmentShader),
                { v, f -> it.descriptorBuilder(v, f) }, it
            )
        }
    }


    fun reloadOnChange(shaderSource: ShaderSource) {
        if (shaderSource is ShaderSource.HotFile) {
            fsWatcher.onFileChanged(shaderSource.getSourceFile()) {
                runBlocking {
                    // Small hack to see if the new shader source is valid - try to compile it and see if it fails
                    val surface = surfaceLifecycle.assertValue
                    surface.device.pushErrorScope(GPUErrorFilter.Validation)
                    val module = surfaceLifecycle.assertValue.device.createShaderModule(ShaderModuleDescriptor(loadShader(shaderSource)))
                    val error = surface.device.popErrorScope().getOrThrow()
                    module.close()
                    if (error == null) {
                        lifecycle.restart()
                    } else {
                        println("Failed to compile new shader for reload: $error")
                    }
                }

            }
        }
    }

    if (HOT_RELOAD_SHADERS) {
        reloadOnChange(vertexShader)
        reloadOnChange(fragmentShader)
    }

    return lifecycle
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadShader(source: ShaderSource): String {
    val code = when (source) {
        // Load directly from source when hot reloading
        is ShaderSource.HotFile -> if (HOT_RELOAD_SHADERS) {
            Thread.sleep(100)
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
