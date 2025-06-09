package io.github.natanfudge.fn.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.compose.utils.DagLayout
import io.github.natanfudge.fn.compose.utils.FreeformMovement
import io.github.natanfudge.fn.compose.utils.TransformationMatrix2D
import io.github.natanfudge.fn.compose.utils.rememberFreeformMovementState
import io.github.natanfudge.fn.core.ProcessLifecycle
import io.github.natanfudge.fn.util.Lifecycle

@Composable fun LifecycleTree(modifier: Modifier = Modifier) {
    FreeformMovement(transform = rememberFreeformMovementState(TransformationMatrix2D(scale = 0.2f)), modifier) {
        LifecycleTreeUi(ProcessLifecycle)
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