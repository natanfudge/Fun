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
     * If [PipelineManager.hotReloadShaders] is true, changes to this file will be auto-reloaded.
     */
    data class HotFile(val path: String) : ShaderSource
}

@OptIn(ExperimentalResourceApi::class)
class PipelineManager(
    private val device: GPUDevice,
    val presentationFormat: GPUTextureFormat,
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
) : AutoCloseable {
    private var _pipeline: GPURenderPipeline? = null

    val pipeline get() = _pipeline ?: error("Pipeline not initialized yet. This should never happen")

    private val fileSystemWatcher = FileSystemWatcher()

    /**
     * Should be called every frame for hot reloading shaders.
     */
    fun poll() {
        if (hotReloadShaders) {
            fileSystemWatcher.poll()
        }
    }

    private val closeContext = AutoCloseImpl()

    init {
        // SLOW: consider making this async
        runBlocking {
            rebuildPipeline()

            if (hotReloadShaders) {
                var watchedUri: Path? = null
                // It's important to watch and reload the SOURCE file and not the built file so we don't have to rebuild
                if (vertexShader is ShaderSource.HotFile) {
                    watchedUri = vertexShader.getSourceFile().parent!!
                    reloadOnDirectoryChange(watchedUri)
                }

                if (fragmentShader is ShaderSource.HotFile) {
                    val fragmentUri = fragmentShader.getSourceFile().parent!!
                    // Avoid reloading twice when both shaders are in the same directory
                    if (fragmentUri != watchedUri) {
                        reloadOnDirectoryChange(fragmentUri)
                    }
                }
            }
        }
    }

    // HACK: this might not work always
    private fun ShaderSource.HotFile.getSourceFile() = Path("src/main/composeResources/", fullPath())

    private fun reloadOnDirectoryChange(dir: Path) {
        println("Listening to shadffer changes at $dir!")
        fileSystemWatcher.onDirectoryChanged(dir) {
            // SLOW: consider making this async
            runBlocking {
                println("Reloading shaders at '${dir}'")
                rebuildPipeline()
            }
        }
    }

    private var pipelineIndex = 0

    private suspend fun rebuildPipeline() = with(closeContext) {
        if (_pipeline != null) {
            _pipeline!!.close()
        }
        val (vertex, fragment) = loadShaders()
        _pipeline = device.createRenderPipeline(
            RenderPipelineDescriptor(
                layout = null,
                vertex = VertexState(
                    module = vertex,
                    entryPoint = "vs_main"
                ),
                fragment = FragmentState(
                    module = fragment,
                    targets = listOf(ColorTargetState(presentationFormat)),
                    entryPoint = "fs_main"
                ),
                primitive = PrimitiveState(
                    topology = GPUPrimitiveTopology.TriangleList
                ),
                label = "Pipeline ${pipelineIndex++}"
            )
        ).ac
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
        fileSystemWatcher.close()
        closeContext.close()
    }
}