package io.github.natanfudge.fn.window

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.core.WindowEvent

data class WindowConfig(
    val size: IntSize = IntSize(800,600),
    val initialTitle: String = "Fun",
    // We use vsync so don't limit fps
    val maxFps: Int = Int.MAX_VALUE,
)

interface WindowCallbacks {
    fun onInput(input: WindowEvent)

    fun densityChange(newDensity: Density) {}
}


typealias WindowHandle = Long
