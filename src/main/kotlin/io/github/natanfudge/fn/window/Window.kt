package io.github.natanfudge.fn.window

import androidx.compose.ui.unit.Density
import io.github.natanfudge.fn.core.InputEvent

data class WindowConfig(
    val initialWindowWidth: Int = 800,
    val initialWindowHeight: Int = 600,
    val initialTitle: String = "Fun",
    // We use vsync so don't limit fps
    val maxFps: Int = Int.MAX_VALUE,
)

interface WindowCallbacks {
    fun onInput(input: InputEvent)

    fun densityChange(newDensity: Density) {}
}


typealias WindowHandle = Long
