@file:OptIn(InternalComposeUiApi::class)
@file:Suppress("FunctionName")

package io.github.natanfudge.fn.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.lwjgl.glfw.GLFW.*
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.code
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent


fun AwtMouseEvent(awtMods: Int) = MouseEvent(
    awtComponent, 0, 0, awtMods, 0, 0, 1, false
)

fun AwtMouseWheelEvent(awtMods: Int) = MouseWheelEvent(
    awtComponent, 0, 0, awtMods, 0, 0, 1, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1
)

/**
 * We need this to make text input work, otherwise compose won't recognize key typed events.
 * It's probably still need for mouse events too.
 */
private val awtComponent = object : Component() {}

@OptIn(InternalComposeUiApi::class)
fun typedCharacterToComposeEvent(character: Char): ComposeKeyEvent {
    return ComposeKeyEvent(
        key = Key.Unknown,
        type = KeyEventType.KeyDown,
        codePoint = character.code,
        nativeEvent = KeyEvent(
            awtComponent,
            KeyEvent.KEY_TYPED,
            0,
            0,
            0,
            character,
            KeyEvent.KEY_LOCATION_UNKNOWN
        )
    )
}


@OptIn(InternalComposeUiApi::class)
fun glfwToComposeEvent(keyCode: Int, action: Int, mods: Int) = ComposeKeyEvent(
    key = mapGlfwKeyToComposeKey(keyCode),
    type = mapGlfwActionToKeyEventType(action),
    codePoint = keyCode,
    isCtrlPressed = mods and GLFW_MOD_CONTROL != 0,
    isMetaPressed = mods and GLFW_MOD_SUPER != 0,
    isAltPressed = mods and GLFW_MOD_ALT != 0,
    isShiftPressed = mods and GLFW_MOD_SHIFT != 0
)

// Function to map GLFW key codes to Compose Keys
private fun mapGlfwKeyToComposeKey(glfwKey: Int): Key {
    return when (glfwKey) {
        GLFW_KEY_SPACE -> Key.Spacebar
        GLFW_KEY_APOSTROPHE -> Key.Apostrophe
        GLFW_KEY_COMMA -> Key.Comma
        GLFW_KEY_MINUS -> Key.Minus
        GLFW_KEY_PERIOD -> Key.Period
        GLFW_KEY_SLASH -> Key.Slash
        GLFW_KEY_SEMICOLON -> Key.Semicolon
        GLFW_KEY_EQUAL -> Key.Equals
        GLFW_KEY_A -> Key.A
        GLFW_KEY_B -> Key.B
        GLFW_KEY_C -> Key.C
        GLFW_KEY_D -> Key.D
        GLFW_KEY_E -> Key.E
        GLFW_KEY_F -> Key.F
        GLFW_KEY_G -> Key.G
        GLFW_KEY_H -> Key.H
        GLFW_KEY_I -> Key.I
        GLFW_KEY_J -> Key.J
        GLFW_KEY_K -> Key.K
        GLFW_KEY_L -> Key.L
        GLFW_KEY_M -> Key.M
        GLFW_KEY_N -> Key.N
        GLFW_KEY_O -> Key.O
        GLFW_KEY_P -> Key.P
        GLFW_KEY_Q -> Key.Q
        GLFW_KEY_R -> Key.R
        GLFW_KEY_S -> Key.S
        GLFW_KEY_T -> Key.T
        GLFW_KEY_U -> Key.U
        GLFW_KEY_V -> Key.V
        GLFW_KEY_W -> Key.W
        GLFW_KEY_X -> Key.X
        GLFW_KEY_Y -> Key.Y
        GLFW_KEY_Z -> Key.Z
        GLFW_KEY_LEFT_BRACKET -> Key.LeftBracket
        GLFW_KEY_BACKSLASH -> Key.Backslash
        GLFW_KEY_RIGHT_BRACKET -> Key.RightBracket
        GLFW_KEY_GRAVE_ACCENT -> Key.Grave
        GLFW_KEY_ESCAPE -> Key.Escape
        GLFW_KEY_ENTER -> Key.Enter
        GLFW_KEY_TAB -> Key.Tab
        GLFW_KEY_BACKSPACE -> Key.Backspace
        GLFW_KEY_INSERT -> Key.Insert
        GLFW_KEY_DELETE -> Key.Delete
        GLFW_KEY_LEFT -> Key.DirectionLeft
        GLFW_KEY_RIGHT -> Key.DirectionRight
        GLFW_KEY_UP -> Key.DirectionUp
        GLFW_KEY_DOWN -> Key.DirectionDown
        GLFW_KEY_PAGE_UP -> Key.PageUp
        GLFW_KEY_PAGE_DOWN -> Key.PageDown
        GLFW_KEY_HOME -> Key.Home
        GLFW_KEY_END -> Key.MoveEnd
        GLFW_KEY_CAPS_LOCK -> Key.CapsLock
        GLFW_KEY_SCROLL_LOCK -> Key.ScrollLock
        GLFW_KEY_NUM_LOCK -> Key.NumLock
        GLFW_KEY_PRINT_SCREEN -> Key.PrintScreen
        GLFW_KEY_PAUSE -> Key.MediaPause
        GLFW_KEY_F1 -> Key.F1
        GLFW_KEY_F2 -> Key.F2
        GLFW_KEY_F3 -> Key.F3
        GLFW_KEY_F4 -> Key.F4
        GLFW_KEY_F5 -> Key.F5
        GLFW_KEY_F6 -> Key.F6
        GLFW_KEY_F7 -> Key.F7
        GLFW_KEY_F8 -> Key.F8
        GLFW_KEY_F9 -> Key.F9
        GLFW_KEY_F10 -> Key.F10
        GLFW_KEY_F11 -> Key.F11
        GLFW_KEY_F12 -> Key.F12
        GLFW_KEY_KP_0 -> Key.NumPad0
        GLFW_KEY_KP_1 -> Key.NumPad1
        GLFW_KEY_KP_2 -> Key.NumPad2
        GLFW_KEY_KP_3 -> Key.NumPad3
        GLFW_KEY_KP_4 -> Key.NumPad4
        GLFW_KEY_KP_5 -> Key.NumPad5
        GLFW_KEY_KP_6 -> Key.NumPad6
        GLFW_KEY_KP_7 -> Key.NumPad7
        GLFW_KEY_KP_8 -> Key.NumPad8
        GLFW_KEY_KP_9 -> Key.NumPad9
        GLFW_KEY_LEFT_SHIFT -> Key.ShiftLeft
        GLFW_KEY_LEFT_CONTROL -> Key.CtrlLeft
        GLFW_KEY_LEFT_ALT -> Key.AltLeft
        GLFW_KEY_RIGHT_SHIFT -> Key.ShiftRight
        GLFW_KEY_RIGHT_CONTROL -> Key.CtrlRight
        GLFW_KEY_RIGHT_ALT -> Key.AltRight
        else -> Key.Unknown
    }
}

// Function to map GLFW actions to Compose KeyEventType
private fun mapGlfwActionToKeyEventType(action: Int): KeyEventType {
    return when (action) {
        GLFW_PRESS -> KeyEventType.KeyDown
        GLFW_RELEASE -> KeyEventType.KeyUp
        GLFW_REPEAT -> KeyEventType.KeyDown // Or KeyEventType.AutoRepeat if available
        else -> KeyEventType.Unknown
    }
}


fun getAwtMods(windowHandle: Long): Int {
    var awtMods = 0
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS)
        awtMods = awtMods or InputEvent.BUTTON1_DOWN_MASK
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_2) == GLFW_PRESS)
        awtMods = awtMods or InputEvent.BUTTON2_DOWN_MASK
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_3) == GLFW_PRESS)
        awtMods = awtMods or InputEvent.BUTTON3_DOWN_MASK
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_4) == GLFW_PRESS)
        awtMods = awtMods or (1 shl 14)
    if (glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_5) == GLFW_PRESS)
        awtMods = awtMods or (1 shl 15)
    if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS || glfwGetKey(
            windowHandle,
            GLFW_KEY_RIGHT_CONTROL
        ) == GLFW_PRESS
    )
        awtMods = awtMods or InputEvent.CTRL_DOWN_MASK
    if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS || glfwGetKey(
            windowHandle,
            GLFW_KEY_RIGHT_SHIFT
        ) == GLFW_PRESS
    )
        awtMods = awtMods or InputEvent.SHIFT_DOWN_MASK
    if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_ALT) == GLFW_PRESS || glfwGetKey(
            windowHandle,
            GLFW_KEY_RIGHT_ALT
        ) == GLFW_PRESS
    )
        awtMods = awtMods or InputEvent.ALT_DOWN_MASK
    return awtMods
}