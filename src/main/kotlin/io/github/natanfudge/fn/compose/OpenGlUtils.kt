package io.github.natanfudge.fn.compose

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT32
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL43.GL_DEBUG_SOURCE_APPLICATION
import org.lwjgl.opengl.GL43.glObjectLabel
import org.lwjgl.opengl.GL43.glPopDebugGroup
import org.lwjgl.opengl.GL43.glPushDebugGroup
import java.nio.FloatBuffer

internal inline fun createFramebuffer(name: () -> String, usage: FrameBufferContext.() -> Unit = {}): Int {
    val framebuffer = glGenFramebuffers()

    // We will now speak of this framebuffer
    withFrameBuffer(framebuffer) {
        fastGlDebugObjectLabel(GL_FRAMEBUFFER, framebuffer) { name() }
        usage()
    }
    return framebuffer
}

internal inline fun createColorTexture(width: Int, height: Int, name: (Int) -> String, config: () -> Unit = {}): Int {
    // Store 8 bit colors
    return createTexture(width, height, GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, name, config)
}


internal inline fun createDepthTexture(width: Int, height: Int, name: (Int) -> String, config: () -> Unit = {}): Int {
    // Store 32 bits floats for depth
    return createTexture(width, height, GL_DEPTH_COMPONENT32, GL_DEPTH_COMPONENT, GL_FLOAT, name, config)
}

internal inline fun createTexture(
    width: Int,
    height: Int,
    internalFormat: Int,
    purpose: Int,
    type: Int,
    name: (Int) -> String,
    config: () -> Unit = {},
): Int {
    val texture = glGenTextures()
    withTexture(texture) {
//        glBindTexture(GL_TEXTURE_2D, texture)
        fastGlDebugObjectLabel(GL_TEXTURE, texture) { name(texture) }

        // Allocates memory for the texture.
        glTexImage2D(
            GL_TEXTURE_2D,
            // We don't care about mipmaps
            0,
            internalFormat,
            width,
            height,
            // This feature is mostly historical and has limited practical use in modern OpenGL
            0,
            purpose,
            type,
            // No initial data
            null as FloatBuffer?
        )

        config()
    }

    return texture
}

private const val GL_DEBUG = true

internal inline fun withTexture(textureId: Int, code: () -> Unit) {
    val previouslyBoundTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
    glBindTexture(GL_TEXTURE_2D, textureId)
    code()
    glBindTexture(GL_TEXTURE_2D, previouslyBoundTexture)
}


/**
 * Labels a GL object. Note that the GL object actually needs to be bound!
 *
 * Labels a named object identified within a namespace.
 *
 * @param namespace the namespace from which the name of the object is allocated. One of:
 * BUFFER
 * SHADER
 * PROGRAM
 * QUERY
 * PROGRAM_PIPELINE
 * SAMPLER
 * VERTEX_ARRAY
 * TEXTURE
 * RENDERBUFFER
 * FRAMEBUFFER
 * TRANSFORM_FEEDBACK
 *  @param obj       the name of the object to label
 * @param label      a string containing the label to assign to the object
 *
 * @see <a href="https://docs.gl/gl4/glObjectLabel">Reference Page</a>
 */
internal inline fun fastGlDebugObjectLabel(namespace: Int, obj: Int, label: () -> String) {
    if (GL_DEBUG) {
        glObjectLabel(namespace, obj, label())
    }
}

internal object FrameBufferContext

internal fun attachDepthTextureToFrameBuffer(depthTexture: Int) {
    glFramebufferTexture2D(
        GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture,
        // Don't care about mipmap
        0
    )
}

internal fun attachColorTextureToFrameBuffer(colorTexture: Int) {
    glFramebufferTexture2D(
        GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture,
        // Don't care about mipmap
        0
    )
}

internal inline fun <T> withFrameBuffer(frameBufferId: Int, code: FrameBufferContext.() -> T): T {
    val previousFrameBuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
    glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId)
    val value = code(FrameBufferContext)
    // Unbind the framebuffer, avoid set effects
    glBindFramebuffer(GL_FRAMEBUFFER, previousFrameBuffer)
    return value
}

internal inline fun <T> glDebugGroup(id: Int, groupName: () -> String, code: () -> T): T {
    if (GL_DEBUG) {
        glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, id, groupName())
        val value = code()
        glPopDebugGroup()
        return value
    } else {
        return code()
    }
}

//
///**
// * Handles setting up the OpenGL Shader program.
// */
//class ShaderProgram(val programId: Int) {
//    companion object {
//        //        fun create(vertexShader: String, fragmentShader: String, vertexShaderName: String, fragmentShaderName: String): ShaderProgram = create(listOf(vertexShader), listOf(fragmentShader))
//        fun load(vertexShader: String, fragmentShader: String) = load(listOf(vertexShader), listOf(fragmentShader))
//        fun load(vertexShaders: List<String>, fragmentShaders: List<String>): ShaderProgram {
//            val programId = GL20.glCreateProgram()
//            if (programId == 0) {
//                throw RuntimeException("Could not create Shader")
//            }
//
//
//            val shaderModules = buildList {
//                for (shader in vertexShaders) {
//                    add(createShader(programId, shader, GL_VERTEX_SHADER))
//                }
//                for (shader in fragmentShaders) {
//                    add(createShader(programId, shader, GL_FRAGMENT_SHADER))
//                }
//            }
//
//            link(programId, shaderModules)
//            if (GL_DEBUG) validate(programId)
//            return ShaderProgram(programId)
//        }
////        fun load(vertexShaderFiles: List<String>, fragmentShaderFiles: List<String>): ShaderProgram = create(
////            vertexShaderFiles.map { it to readResource(it) },
////            fragmentShaderFiles.map { it to readResource(it) }
////        )
//
//        private fun createShader(programId: Int, shaderCode: String, shaderType: Int): Int {
//            val shaderId = GL20.glCreateShader(shaderType)
//            if (shaderId == 0) {
//                throw RuntimeException("Error creating shader. Type: $shaderType")
//            }
//
//            GL20.glShaderSource(shaderId, shaderCode)
//            GL20.glCompileShader(shaderId)
//
//            if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
//                throw RuntimeException("Error compiling Shader $shaderCode: " + GL20.glGetShaderInfoLog(shaderId, 1024))
//            }
//
//            GL20.glAttachShader(programId, shaderId)
//
//            return shaderId
//        }
//
//        private fun link(programId: Int, shaderModules: List<Int>) {
//            GL20.glLinkProgram(programId)
//            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
//                throw RuntimeException("Error linking Shader code: " + GL20.glGetProgramInfoLog(programId, 1024))
//            }
//
//            shaderModules.forEach(Consumer { s: Int? -> GL20.glDetachShader(programId, s!!) })
//            shaderModules.forEach(Consumer { shader: Int? -> GL30.glDeleteShader(shader!!) })
//        }
//
//        private fun validate(programId: Int) {
//            GL20.glValidateProgram(programId)
//            if (GL20.glGetProgrami(programId, GL20.GL_VALIDATE_STATUS) == 0) {
//                throw RuntimeException("Error validating Shader code: " + GL20.glGetProgramInfoLog(programId, 1024))
//            }
//        }
//    }
//
//    inline fun withBind(usage: () -> Unit) {
//        // Very important for skia interop - remember what the previous program was and restore it after we are done.
//        val oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
//
//        _bind()
//        usage()
//        _unbind(oldProgram)
//    }
//
//
//    @PublishedApi
//    internal fun _bind() {
//        GL20.glUseProgram(programId)
//    }
//
//    fun close() {
//        _unbind(0)
//        if (programId != 0) {
//            GL20.glDeleteProgram(programId)
//        }
//    }
//
//
//    @PublishedApi
//    internal fun _unbind(programId: Int) {
//        GL20.glUseProgram(programId)
//    }
//
//}