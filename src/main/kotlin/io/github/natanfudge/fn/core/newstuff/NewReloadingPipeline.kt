package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.core.HOT_RELOAD_SHADERS
import io.github.natanfudge.fn.files.readString
import io.github.natanfudge.fn.util.EventEmitter
import io.github.natanfudge.fn.util.EventStream
import io.github.natanfudge.fn.util.closeAll
import io.github.natanfudge.fn.webgpu.ShaderSource
import io.ygdrasil.webgpu.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import natan.`fun`.generated.resources.Res
import org.jetbrains.compose.reload.core.Either
import org.jetbrains.compose.reload.core.Left
import org.jetbrains.compose.reload.core.Right
import org.jetbrains.compose.resources.ExperimentalResourceApi

//TODO: Have a utility for automatically assigning a counter to instances

var nextPipelineIndex = 0


class ActivePipeline(
    val ctx: NewWebGPUContext,
    val vertexSource: ShaderSource,
    val fragmentSource: ShaderSource,
    val pipelineDescriptorBuilder: PipelineDescriptorBuilder,
    val pipelineLoaded: EventEmitter<GPURenderPipeline>,
    val pipelineClosed: EventEmitter<Unit>,
) : AutoCloseable {
    var pipeline: GPURenderPipeline? = null
    private var vertexShader: GPUShaderModule? = null
    private var fragmentShader: GPUShaderModule? = null


    fun reload() {
        val (vertex, fragment) = runBlocking {
            if (vertexSource == fragmentSource) {
                val shader = loadShader(vertexSource)
                shader to shader
            } else {
                loadShader(vertexSource) to loadShader(fragmentSource)
            }
        }

        println("Loading pipeline with device num ${ctx.index}")
        val vertexShader = safeCreateShaderModule(vertex)
        val fragmentShader = safeCreateShaderModule(fragment)
        println("Created shader module")
        if (vertexShader is Left && fragmentShader is Left) {
            // Success: reload pipeline
            close()
            this.vertexShader = vertexShader.value
            this.fragmentShader = fragmentShader.value
            println("Creating render pipeline, vertex len = ${vertex.length}, fragment len = ${vertex.length}")
//            println("Setting pipeline on ReloadingPipeline num $index")
            this.pipeline = ctx.device.createRenderPipeline(pipelineDescriptorBuilder(ctx, this.vertexShader!!, this.fragmentShader!!))
            println("Created render pipeline")
            pipelineLoaded(this.pipeline!!)
        } else {
            // Fail - do nothing
            if (vertexShader is Right) {
                println("Error while compiling vertex shader: ${vertexShader.value}")
            }
            if (fragmentShader is Right && (vertexSource != fragmentSource || vertexShader is Left)) {
                println("Error while compiling fragment shader: ${fragmentShader.value}")
            }

            // TODO: have some sort of plan B in case it fails in first initialization, because we won't have any existing shader code to reuse.
            this.vertexShader = null
            this.fragmentShader = null
            this.pipeline = null
        }
    }

    init {
        reload()
    }

    override fun close() {
        println("Closing pipeline dependants")
        pipelineClosed.emit(Unit)
        println("Closing pipeline $pipeline")
        //TODo: we can't
        pipeline?.close()
        pipeline = null
        println("Closing vertex")
        vertexShader?.close()
        vertexShader = null
        println("Closing fragment")
        fragmentShader?.close()
        fragmentShader = null
//        closeAll(pipeline, vertexShader, fragmentShader)
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

typealias PipelineDescriptorBuilder = NewWebGPUContext.(vertex: GPUShaderModule, fragment: GPUShaderModule) -> GPURenderPipelineDescriptor

class NewReloadingPipeline(
    label: String, val surface: NewWebGPUSurface, val vertexSource: ShaderSource, val fragmentSource: ShaderSource = vertexSource,
    val pipelineDescriptorBuilder: PipelineDescriptorBuilder,
) : NewFun("ReloadingPipeline-$label") {
    val pipelineLoaded by memo { EventStream.create<GPURenderPipeline>() }
    val pipelineClosed by memo { EventStream.create<Unit>() }
    // NOTE: we don't want to depend on the WebGPUSurface directly because it is actually recreated every refresh, in contrast with the window that
    // is recreated when the window is actually recreated. This is kind of confusing and I would like to do something better.
    val active by sideEffect(surface.window, vertexSource, fragmentSource) {
        ActivePipeline(surface.webgpu, vertexSource, fragmentSource, pipelineDescriptorBuilder, pipelineLoaded, pipelineClosed)
    }

    val pipeline: GPURenderPipeline get() = active.pipeline!!


    val index = nextPipelineIndex++

    override fun init() {
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
            println("Re-registering callback")
            context.fsWatcher.onFileChanged(shaderSource.getSourceFile()) {
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
