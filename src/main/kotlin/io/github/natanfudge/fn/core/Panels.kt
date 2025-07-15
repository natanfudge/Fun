package io.github.natanfudge.fn.core

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import io.github.natanfudge.fn.compose.utils.clickableWithNoIndication

data class ComposePanel(val modifier:  BoxScope. () -> Modifier, val content: @Composable BoxScope.() -> Unit, val panels: Panels): AutoCloseable {
    override fun close() {
        panels.closePanel(this)
    }
}


class Panels {
    private val panelList = mutableStateListOf<ComposePanel>()

    internal fun clearPanels() {
        panelList.clear()
    }

    fun addPanel(modifier: BoxScope. () -> Modifier = {Modifier}, content: @Composable BoxScope.() -> Unit): ComposePanel {
        val panel = ComposePanel(modifier, content, this)
        panelList.add(panel)
        return panel
    }

    fun closePanel(panel: ComposePanel) {
        panelList.remove(panel)
    }

    //    var blockMouseEventsOnPanel = true
    var acceptMouseEvents = true

    /**
     * Allows placing "Panels", which block clicks from reaching the game when they are clicked.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun PanelSupport() {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().focusable().onPointerEvent(PointerEventType.Press) {
                // Allow clicking outside of the GUI
                acceptMouseEvents = true
                // The clickable thing is just for it to draw focus
            }.clickableWithNoIndication { }.onPointerEvent(PointerEventType.Enter) {
                acceptMouseEvents = true
            }
            )

            for (panel in panelList) {
                Box(
                    panel.modifier(this)
                        .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) {
                            // Block clicks
                            acceptMouseEvents = false
                        }.onPointerEvent(PointerEventType.Enter) {
//                            println("Will not accept mouse events")
                        }
                ) {
                    panel.content(this)
                }
            }

        }
    }

}

