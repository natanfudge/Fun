package io.github.natanfudge.fn.compose.utils

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class Holder<T>(var value: T) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

fun Modifier.clickableWithNoIndication(callback: () -> Unit) = clickable(indication = null, interactionSource = null, onClick = callback)

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onPointerChangeEvent(
    type: PointerEventType,
    onEvent: (PointerInputChange) -> Unit,
): Modifier = onPointerEvent(type) {
    it.changes.forEach { change ->
        onEvent(change)
    }
}

fun composeApp(content: @Composable () -> Unit) {
    application {
        Window(onCloseRequest = ::exitApplication) {
            FunTheme {
                Surface {
                    content()
                }
            }
        }
    }
}

@Composable
fun FunTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        content()
    }
}