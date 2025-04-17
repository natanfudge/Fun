package io.github.natanfudge.fu.window

import androidx.compose.runtime.Composable

data class WindowConfig(
    val compose: ComposeConfig = ComposeConfig(),
    val initialWindowWidth: Int = 800,
    val initialWindowHeight: Int = 600,
    val initialTitle: String = "Fun",
)

data class ComposeConfig(
    val content: @Composable () -> Unit = {},
    val beforeDraw: () -> Unit = {},
    val afterDraw: () -> Unit = {},
)