package io.github.natanfudge.fn.base

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.core.ComposePanelPlacer
import io.github.natanfudge.fn.core.FunContext

class ErrorNotifications(context: FunContext) {
    private var error: Throwable? by mutableStateOf(null)

    init {
        context.events.guiError.listen {
            this.error = it
        }
        context.addFunPanel {
            if (error != null) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, "error", tint = Color.Red, modifier = Modifier.padding(10.dp).size(50.dp))
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = "Error: " + (error!!.message ?: "Unknown error"),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                color = Color.Red
                            )
                            Text(error!!.stackTraceToString())
                            Button(onClick = { error = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }
    }
}