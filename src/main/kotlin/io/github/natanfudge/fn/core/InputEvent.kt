package io.github.natanfudge.fn.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skiko.currentNanoTime

sealed interface InputEvent {
    object CloseButtonPressed : InputEvent
    data class PointerEvent(
        val eventType: PointerEventType,
        val position: Offset,
        val scrollDelta: Offset = Offset.Zero,
        val timeMillis: Long = currentTimeForEvent(),
        val type: PointerType = PointerType.Mouse,
        val buttons: PointerButtons? = null,
        val keyboardModifiers: PointerKeyboardModifiers? = null,
        val nativeEvent: Any? = null,
        val button: PointerButton? = null,
    ) : InputEvent

    data class KeyEvent(val event: androidx.compose.ui.input.key.KeyEvent) : InputEvent
    data class WindowMove(val offset: IntOffset) : InputEvent
    data class WindowResize(val size: IntSize): InputEvent
    data class DensityChange(val density: Density): InputEvent
    object WindowClose: InputEvent
}


private fun currentTimeForEvent(): Long = (currentNanoTime() / 1E6).toLong()

const val HOT_RELOAD_SHADERS = true

