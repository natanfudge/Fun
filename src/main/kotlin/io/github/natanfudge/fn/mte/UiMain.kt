package io.github.natanfudge.fn.mte

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.compose.AsyncImage
import natan.`fun`.generated.resources.Res
import java.net.URI
import kotlin.io.path.absolutePathString
import kotlin.io.path.toPath

fun main() {
    application {
        Window(::exitApplication) {
            val actualFile = URI(Res.getUri("drawable/dawn.png")).toPath().absolutePathString()
            AsyncImage(actualFile, contentDescription = null)
        }
    }
}