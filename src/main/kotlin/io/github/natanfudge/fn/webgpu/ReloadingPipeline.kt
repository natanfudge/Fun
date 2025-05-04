package io.github.natanfudge.fn.webgpu

import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.files.readString
import io.ygdrasil.webgpu.*
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

@OptIn(ExperimentalResourceApi::class)
class ReloadingPipeline(
    private val device: GPUDevice,
    private val fsWatcher: FileSystemWatcher,
//    val presentationFormat: GPUTextureFormat,
    val vertexShader: ShaderSource,
    /**
     * If null, the vertex shader source will be used for the fragment shader as well
     */
    val fragmentShader: ShaderSource? = null,
    /**
     * If true, changes in [ShaderSource.HotFile] shaders will be listened to, and the pipeline will be reloaded with the new changes.
     * This has a large overhead and should not be used in production.
     */
    val hotReloadShaders: Boolean = true,
    val config: (vertex: GPUShaderModule, fragment: GPUShaderModule) -> RenderPipelineDescriptor
) : AutoCloseable {
    private var _pipeline: GPURenderPipeline? = null

    /**
     * Gets the currently active pipeline. Note that this value can change over time so atm you should access this every frame
     * SLOW: maybe add some "dependant objects" thing to not recreate them every frame
     */
    val pipeline get() = _pipeline ?: error("Pipeline not initialized yet. This should never happen")

    private val closeContext = AutoCloseImpl()



    private var vertexChangeListener: FileSystemWatcher.Key? = null
    private var fragmentChangeListener: FileSystemWatcher.Key? = null

    init {
        // SLOW: consider making this async
        runBlocking {
            rebuildPipeline()

            if (hotReloadShaders) {
                var watchedUri: Path? = null
                // It's important to watch and reload the SOURCE file and not the built file so we don't have to rebuild
                if (vertexShader is ShaderSource.HotFile) {
                    watchedUri = vertexShader.getSourceFile().parent!!
                    vertexChangeListener = reloadOnDirectoryChange(watchedUri)
                }

                if (fragmentShader is ShaderSource.HotFile) {
                    val fragmentUri = fragmentShader.getSourceFile().parent!!
                    // Avoid reloading twice when both shaders are in the same directory
                    if (fragmentUri != watchedUri) {
                        fragmentChangeListener = reloadOnDirectoryChange(fragmentUri)
                    }
                }
            }
        }
    }

    // HACK: this might not work always
    private fun ShaderSource.HotFile.getSourceFile() = Path("src/main/composeResources/", fullPath())

    private fun reloadOnDirectoryChange(dir: Path): FileSystemWatcher.Key {
        println("Listening to shadffer changes at $dir!")
        return fsWatcher.onDirectoryChanged(dir) {
            // SLOW: consider making this async
            runBlocking {
                println("Reloading shaders at '${dir}'")
                rebuildPipeline()
            }
        }
    }


    private suspend fun rebuildPipeline() = with(closeContext) {
        if (_pipeline != null) {
            _pipeline!!.close()
        }
        val (vertex, fragment) = loadShaders()
        _pipeline = device.createRenderPipeline(config(vertex, fragment)).ac
    }

    /**
     * Returns the vertex and fragment shaders
     */
    private suspend fun loadShaders(): Pair<GPUShaderModule, GPUShaderModule> {
        val vertex = loadShader(vertexShader)
        val fragment = if (fragmentShader == null) vertex else loadShader(fragmentShader)
        return vertex to fragment
    }

    private fun ShaderSource.HotFile.fullPath() = "files/shaders/${path}.wgsl"

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun loadShader(source: ShaderSource): GPUShaderModule = with(closeContext) {
        val source = when (source) {
            // Load directly from source when hot reloading
            is ShaderSource.HotFile -> if (hotReloadShaders) source.getSourceFile().readString()
            else Res.readBytes(source.fullPath()).decodeToString()

            is ShaderSource.RawString -> source.shader
        }

        device.createShaderModule(
            ShaderModuleDescriptor(code = source)
        ).ac
    }

    override fun close() {
        vertexChangeListener?.close()
        fragmentChangeListener?.close()
        closeContext.close()
    }
}