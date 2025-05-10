package io.github.natanfudge.fn.webgpu

import io.github.natanfudge.fn.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.files.readString
import io.github.natanfudge.fn.util.BindableLifecycle
import io.github.natanfudge.fn.util.bindBindable
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.util.restart
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

class FunPipeline(
    vertexShaderCode: String,
    fragmentShaderCode: String,
    descriptorBuilder: (GPUShaderModule, GPUShaderModule) -> GPURenderPipelineDescriptor,
    val ctx: WebGPUContext, //TODO: I think it's a mistake putting this here this when the pipeline changes ctx doesn't change.
    // On the other hand when the surface changes ctx does change and then you get old values for the children of this
) : AutoCloseable {
    val vertexShader = ctx.device.createShaderModule(ShaderModuleDescriptor(code = vertexShaderCode))
    val fragmentShader = ctx.device.createShaderModule(ShaderModuleDescriptor(code = fragmentShaderCode))

    val pipeline = ctx.device.createRenderPipeline(descriptorBuilder(vertexShader, fragmentShader))
    override fun close() {
        closeAll(vertexShader, fragmentShader, pipeline)
    }
}

fun createReloadingPipeline(
    surfaceLifecycle: BindableLifecycle<*, WebGPUContext>,
    fsWatcher: FileSystemWatcher,
    vertexShader: ShaderSource,
    fragmentShader: ShaderSource = vertexShader,
    descriptorBuilder: WebGPUContext.(GPUShaderModule, GPUShaderModule) -> GPURenderPipelineDescriptor,
): BindableLifecycle<WebGPUContext, FunPipeline> {
    val lifecycle = surfaceLifecycle.bindBindable {
        // SLOW: this should prob not be blocking like this
        runBlocking {
            FunPipeline(
                vertexShaderCode = loadShader(vertexShader),
                fragmentShaderCode = loadShader(fragmentShader),
                { v, f -> it.descriptorBuilder(v, f) }, it
            )
        }
    }

    fun reloadOnDirectoryChange(dir: Path): FileSystemWatcher.Key {
        println("Listening to shader changes at $dir!")
        return fsWatcher.onDirectoryChanged(dir) {
            lifecycle.restart(surfaceLifecycle.assertValue)
        }
    }

    if (HOT_RELOAD_SHADERS) {
        var watchedUri: Path? = null
        // It's important to watch and reload the SOURCE file and not the built file so we don't have to rebuild
        if (vertexShader is ShaderSource.HotFile) {
            watchedUri = vertexShader.getSourceFile().parent!!
            // HACK: we are not closing this for now
            reloadOnDirectoryChange(watchedUri)
        }

        if (fragmentShader is ShaderSource.HotFile) {
            val fragmentUri = fragmentShader.getSourceFile().parent!!
            // Avoid reloading twice when both shaders are in the same directory
            if (fragmentUri != watchedUri) {
                // HACK: we are not closing this for now
                reloadOnDirectoryChange(fragmentUri)
            }
        }
    }

    return lifecycle
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadShader(source: ShaderSource): String {
    val code = when (source) {
        // Load directly from source when hot reloading
        is ShaderSource.HotFile -> if (HOT_RELOAD_SHADERS) {
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
