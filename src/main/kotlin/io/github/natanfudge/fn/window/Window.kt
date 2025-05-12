package io.github.natanfudge.fn.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import io.github.natanfudge.fn.webgpu.AutoClose
import org.jetbrains.skiko.currentNanoTime

data class WindowConfig(
    val initialWindowWidth: Int = 800,
    val initialWindowHeight: Int = 600,
    val initialTitle: String = "Fun",
    val maxFps: Int = 60,
)

//fun RepeatingWindowCallbacks.withInit(() -> Unit)

//TODO: needs to be refactored
interface RepeatingWindowCallbacks {
    //TODO: we don't use this anymore
    fun AutoClose.frame(deltaMs: Double) {}

    //TODO: this one can be removed we don't use it
    /**
     * Will be called once on startup as well
     */
    fun resize(width: Int, height: Int) {}

    /**
     * You should close the window here
     */
    fun windowClosePressed() {}
    fun pointerEvent(
        eventType: PointerEventType,
        position: Offset,
        scrollDelta: Offset = Offset.Zero,
        timeMillis: Long = currentTimeForEvent(),
        type: PointerType = PointerType.Mouse,
        buttons: PointerButtons? = null,
        keyboardModifiers: PointerKeyboardModifiers? = null,
        nativeEvent: Any? = null,
        button: PointerButton? = null,
    ) {
    }

    fun keyEvent(
        event: KeyEvent,
    ) {
    }

    fun densityChange(newDensity: Density) {}
    fun windowMove(x: Int, y: Int){}
}

//interface WindowCallbacks : RepeatingWindowCallbacks {
//    fun init(handle: WindowHandle) {}
//}

fun RepeatingWindowCallbacks.combine(other: RepeatingWindowCallbacks) = ComposedWindowCallback(this, other)

/**
 * Combines two [RepeatingWindowCallbacks] to transmit the events to both of them, first to the [first] and then to the [second].
 */
class ComposedWindowCallback(val first: RepeatingWindowCallbacks, val second: RepeatingWindowCallbacks): RepeatingWindowCallbacks {
//    override fun init(handle: WindowHandle) {
//        first.init(handle)
//        second.init(handle)
//    }

    override fun densityChange(newDensity: Density) {
        first.densityChange(newDensity)
        second.densityChange(newDensity)
    }

    override fun AutoClose.frame(deltaMs: Double) {
//        println("First part frame")
        with(first) {
            frame(deltaMs)
        }
//        println("Second part frame")
        with(second){
            frame(deltaMs)
        }
    }

    override fun keyEvent(event: KeyEvent) {
        first.keyEvent(event)
        second.keyEvent(event)
    }

    override fun pointerEvent(
        eventType: PointerEventType,
        position: Offset,
        scrollDelta: Offset,
        timeMillis: Long,
        type: PointerType,
        buttons: PointerButtons?,
        keyboardModifiers: PointerKeyboardModifiers?,
        nativeEvent: Any?,
        button: PointerButton?
    ) {
        first.pointerEvent(eventType, position, scrollDelta, timeMillis, type, buttons, keyboardModifiers, nativeEvent, button)
        second.pointerEvent(eventType, position, scrollDelta, timeMillis, type, buttons, keyboardModifiers, nativeEvent, button)
    }

    override fun resize(width: Int, height: Int) {
        first.resize(width, height)
        second.resize(width,height)
    }

    override fun windowClosePressed() {
        first.windowClosePressed()
        second.windowClosePressed()
    }

    override fun windowMove(x: Int, y: Int) {
        first.windowMove(x,y)
        second.windowMove(x,y)
    }
}

private fun currentTimeForEvent(): Long = (currentNanoTime() / 1E6).toLong()


typealias WindowHandle = Long
