package io.github.natanfudge.fn.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.webgpu.AutoClose
import org.jetbrains.skiko.currentNanoTime

data class WindowConfig(
    val initialWindowWidth: Int = 800,
    val initialWindowHeight: Int = 600,
    val initialTitle: String = "Fun",
    // We use vsync so don't limit fps
    val maxFps: Int = Int.MAX_VALUE,
)

interface WindowCallbacks {
    fun onInput(input: InputEvent)

//    /**
//     * You should close the window here
//     */
//    fun windowClosePressed() {}
//    fun pointerEvent(
//        eventType: PointerEventType,
//        position: Offset,
//        scrollDelta: Offset = Offset.Zero,
//        timeMillis: Long = currentTimeForEvent(),
//        type: PointerType = PointerType.Mouse,
//        buttons: PointerButtons? = null,
//        keyboardModifiers: PointerKeyboardModifiers? = null,
//        nativeEvent: Any? = null,
//        button: PointerButton? = null,
//    ) {
//    }
//
//    fun keyEvent(
//        event: KeyEvent,
//    ) {
//    }

    fun densityChange(newDensity: Density) {}
//    fun windowMove(x: Int, y: Int){}
}


private fun currentTimeForEvent(): Long = (currentNanoTime() / 1E6).toLong()


typealias WindowHandle = Long
