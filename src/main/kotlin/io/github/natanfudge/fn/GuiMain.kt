package io.github.natanfudge.fn

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.natanfudge.fn.core.SimpleLogger
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.staticHotReloadScope

@OptIn(DelicateHotReloadApi::class)
fun main() {
    staticHotReloadScope.invokeAfterHotReload {
        SimpleLogger.info("Reload"){"Reload!"}
    }
    application {
        Window(::exitApplication) {
            Button({}) {
                Text("Foo")
            }
        }
    }
}