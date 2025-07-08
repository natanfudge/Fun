package io.github.natanfudge.fn.base

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.core.ComposePanelPlacer
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.gltf.clearModelCache
import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.network.state.collectAsState

class RestartButtonsMod(val context: FunContext) : FunMod {
    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun ComposePanelPlacer.gui() {
        FunPanel(Modifier.align(Alignment.CenterStart)) {
            Surface(color = Color.Transparent) {
                Column {
                    Button(onClick = { ProcessLifecycle.restartByLabels(setOf("WebGPU Surface")) }) {
                        Text("Restart Surface Lifecycle")
                    }

                    Button(onClick = {
                        context.restartApp()
                    }) {
                        Text("Restart App Lifecycle")
                    }
                    Button(onClick = {
                        clearModelCache()
                        context.restartApp()
                    }) {
                        Text("Reload models")
                    }
                    Button(onClick = {
                        if (context.time.stopped) context.time.resume()
                        else context.time.stop()
                    }) {
                        val stopped by context.time.stoppedState.collectAsState()
                        Text("${if (stopped) "Resume" else "Stop"} Game")
                    }
                    Button(onClick = {
                        ProcessLifecycle.restartByLabels(setOf("App Compose binding"))
                    }) {
                        Text("Reapply Compose App")
                    }

                    Button(onClick = {
                        FunHotReload.reloadStarted.emit(Unit)
                        FunHotReload.reloadEnded.emit(Unit)
                    }) {
                        Text("Emulate Hot Reload")
                    }
                }
            }
        }
    }
}