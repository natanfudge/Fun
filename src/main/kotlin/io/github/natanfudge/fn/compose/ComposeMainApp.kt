@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.natanfudge.fn.compose.utils.DagLayout
import io.github.natanfudge.fn.compose.utils.FreeformMovement
import io.github.natanfudge.fn.compose.utils.LabeledEdgeTree
import io.github.natanfudge.fn.compose.utils.LabeledTreeLayout
import io.github.natanfudge.fn.compose.utils.TransformationMatrix2D
import io.github.natanfudge.fn.compose.utils.rememberFreeformMovementState
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.core.RootLifecycles
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.util.LifecycleData
import kotlinx.coroutines.delay


@Composable
fun ComposeMainApp() {
    println("Recompose ")

    MaterialTheme(colors = darkColors()) {
        Surface {
            var color by remember { mutableStateOf(Color.Red) }
            LaunchedEffect(Unit) {
                delay(500)
                color = Color.Cyan
            }
            // We add a little padding so skia won't flag our color as "the background color" and this will conflict
            // with the fact we override the background color to be transparent.
            Box(Modifier.padding(10.dp).border(1.dp, Color.Green).background(color.copy(alpha = 0.2f))) {
                Column {
                    Button(onClick = {
                        color = if (color == Color.Red) Color.Blue else Color.Red
                    }) {
                        Text("Compose Button", color = Color.White, fontSize = 30.sp)
                    }

                    FreeformMovement(transform = rememberFreeformMovementState(TransformationMatrix2D(scale = 0.2f))) {
                        Row {
                            for (root in RootLifecycles) {
                                LifecycleTreeUi(root)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun LifecycleTreeUi(lifecycle: Lifecycle<*, *>) {
    DagLayout(lifecycle.tree, node = { node, path, canExpand ->
        OutlinedButton(onClick = {
            println("Restarting lifecycle ${node.label}")
            ProcessLifecycle.restartByLabels(setOf(node.label))
        }, shape = CircleShape) {
            Text(node.toString(), Modifier.width(100.dp), textAlign = TextAlign.Center)
        }
    }, horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(50.dp), lineColor = Color.White)
}