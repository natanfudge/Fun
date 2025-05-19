package io.github.natanfudge.fn.files

import kotlinx.io.files.Path
import org.lwjgl.stb.STBImage.stbi_failure_reason
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.stb.STBImage.stbi_load
import org.lwjgl.system.MemoryStack
import kotlin.use

class Image(
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
)

fun readImage(path: Path): Image {
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
        return Image(width, height, bytes)
    }
}