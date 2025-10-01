package io.github.natanfudge.fn.base

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunContextImpl
import io.github.natanfudge.fn.core.exposeAsService
import io.github.natanfudge.fn.core.getContext
import io.github.natanfudge.fn.core.serviceKey
import io.github.natanfudge.fn.render.clearModelCache

class FunDebugPanel : Fun("FunDebugPanel") {

    init {
        addFunPanel({ Modifier.align(Alignment.CenterStart) }) {
            Surface(color = Color.Transparent) {
                Column {
                    Button(onClick = {
                        try {
                            restartApp()
                        } catch (e: Throwable) {
                            // Compose likes eating my stack trace
                            e.printStackTrace()
                            throw e
                        }

                    }) {
                        Text("Restart App")
                    }
                    Button(onClick = {
                        clearModelCache()
                        renderer.clearModels()
                        refreshApp()
                    }) {
                        Text("Reload models")
                    }
                    Button(onClick = { refreshApp() }) {
                        Text("Refresh App")
                    }

                    Button(onClick = {
                        try {
                            (getContext() as FunContextImpl). verifyFunsCloseListeners()
                        } catch (e: Throwable) {
                            // Compose likes eating my stack trace
                            e.printStackTrace()
                            throw e
                        } }) {
                        Text("Verify Listeners")
                    }

                    LoggingConfig()
                }
            }
        }
    }

    @Composable
    private fun LoggingConfig() {
        FunEditor(logger, Modifier.width(300.dp))
    }
}

