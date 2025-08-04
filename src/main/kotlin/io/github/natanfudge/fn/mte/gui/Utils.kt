package io.github.natanfudge.fn.mte.gui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.render.Transform

fun Fun.addDsHudPanel(modifier: BoxScope. () -> Modifier = { Modifier }, content: @Composable BoxScope.() -> Unit) {
    addGui(modifier) {
        DsTheme {
            content()
        }
    }
}

fun Fun.addDsWorldPanel(transform: Transform, canvasWidth: Int, canvasHeight: Int, content: @Composable () -> Unit) {
    addWorldGui(transform, canvasWidth, canvasHeight) {
        DsTheme {
            content()
        }
    }
}

@Composable
private fun DsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        darkColorScheme(
            primary = Color(252, 80, 3)
        )
    ) {
        content()
    }
}

fun Modifier.applyIf(condition: Boolean, modifier: Modifier.() -> Modifier): Modifier {
    return if (condition) modifier() else this
}