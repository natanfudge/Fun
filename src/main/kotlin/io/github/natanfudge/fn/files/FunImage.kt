package io.github.natanfudge.fn.files

import androidx.compose.ui.unit.IntSize
import kotlinx.io.files.Path
import natan.`fun`.generated.resources.Res
import org.lwjgl.stb.STBImage.stbi_failure_reason
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.stb.STBImage.stbi_load
import org.lwjgl.system.MemoryStack
import java.net.URI
import kotlin.io.path.toPath
import kotlin.use

class FunImage(
    val size: IntSize,
    val bytes: ByteArray,
    val path: String?
) {
    companion object {
        fun fromResource(path: String): FunImage {
            val url = URI(Res.getUri(path)).toPath().toKotlin()
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

}

fun readImage(path: Path): FunImage {
    val path = path.toNio().toAbsolutePath().toString()
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