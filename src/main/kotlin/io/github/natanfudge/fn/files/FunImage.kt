package io.github.natanfudge.fn.files

import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.core.FunContextRegistry
import natan.`fun`.generated.resources.Res
import org.jetbrains.compose.resources.MissingResourceException
import org.lwjgl.stb.STBImage.*
import org.lwjgl.stb.STBImageWrite
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.toPath

class FunImage(
    val size: IntSize,
    val bytes: ByteArray,
    val path: String?,
) {
    override fun toString(): String {
        return buildString {
            append("${size.width}x${size.height}")
            if (path != null) append(" ${path.substringAfterLast("\\")}")
        }
    }

    companion object {
        private val placeholder by lazy {
            fromResource("files/placeholder.png")
        }

        fun fromResource(path: String): FunImage {
            val uri = try {
                Res.getUri(path)
            } catch (e: MissingResourceException) {
                FunContextRegistry.getContext().logger.error("Missing Resource", e) {
                    "Missing image file at $path"
                }
                return placeholder
            }
            val url = URI(uri).toPath()
            return readImage(url)
        }
    }

    // Only equate by path to avoid checking byte by byte, this works most of the time.
    override fun equals(other: Any?): Boolean {
        return other is FunImage && other.path == path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }

    fun saveAsPng(path: Path) {
        // Make sure the parent directories exist
        val nioPath = path
        Files.createDirectories(nioPath.parent)

        // RGBA → 4 bytes per pixel
        val stride = size.width * 4

        // Copy the Kotlin ByteArray into a direct ByteBuffer that STB can use
        val buffer = MemoryUtil.memAlloc(bytes.size).apply {
            put(bytes)
            flip()
        }

        try {
            val ok = STBImageWrite.stbi_write_png(
                nioPath.toAbsolutePath().toString(),   // destination file
                size.width,                            // image width
                size.height,                           // image height
                4,                                     // components (RGBA)
                buffer,                                // pixel data
                stride                                 // bytes per row
            )

            if (!ok) {
                throw RuntimeException("Failed to write PNG to $nioPath")
            }
        } finally {
            MemoryUtil.memFree(buffer) // always free native memory
        }
    }

}

fun readImage(path: Path): FunImage {
    val path = path.toAbsolutePath().toString()
    MemoryStack.stackPush().use { stack ->
        val w = stack.mallocInt(1)
        val h = stack.mallocInt(1)
        val channels = stack.mallocInt(1)
        val buf = stbi_load(
            path, w, h, channels,
            // rgba
            4
        )

        if (buf == null) {
            throw RuntimeException("Image file [" + path + "] not loaded: " + stbi_failure_reason());
        }
        val width = w.get()
        val height = h.get()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        buf.flip()
        stbi_image_free(buf)
        return FunImage(IntSize(width, height), bytes, path)
    }
}