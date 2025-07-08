package io.github.natanfudge.fn

import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.staticHotReloadScope

@OptIn(DelicateHotReloadApi::class)
fun main() {
    staticHotReloadScope.invokeAfterHotReload {
        println("Reload!")
    }
    application {
        Window(::exitApplication) {
            Text("Hal2f3")
        }
    }
}