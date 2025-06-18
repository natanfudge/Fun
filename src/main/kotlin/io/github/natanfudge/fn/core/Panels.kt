package io.github.natanfudge.fn.core

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import io.github.natanfudge.fn.compose.utils.clickableWithNoIndication


class Panels {
//    var blockMouseEventsOnPanel = true
    var acceptMouseEvents = true
    /**
     * Allows placing "Panels", which block clicks from reaching the game when they are clicked.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun PanelSupport(panels: @Composable ComposePanelPlacer.() -> Unit) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().focusable().onPointerEvent(PointerEventType.Press) {
                // Allow clicking outside of the GUI
                acceptMouseEvents = true
                // The clickable thing is just for it to draw focus
            }.clickableWithNoIndication { }.onPointerEvent(PointerEventType.Enter) {
                acceptMouseEvents = true
            }
            )

            panels(ComposePanelPlacerWithBoxScope(this) { modifier, panel ->
                Box(
                    modifier
                        .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) {
                            // Block clicks
                            acceptMouseEvents = false
                        }.onPointerEvent(PointerEventType.Enter) {
                            acceptMouseEvents = false
                        }
                ) {
                    panel()
                }
            })

        }
    }

}

/**
 * We want to detect which areas belong to the 2D GUI, so every panel placement goes through this interface.
 */
interface ComposePanelPlacer : BoxScope {
    @Composable
    fun Panel(modifier: Modifier, panel: @Composable BoxScope.() -> Unit)
}

class ComposePanelPlacerWithBoxScope(
    scope: BoxScope, private val placer: @Composable (modifier: Modifier, panel: @Composable (BoxScope.() -> Unit)) -> Unit,
) : ComposePanelPlacer, BoxScope by scope {
    @Composable
    override fun Panel(
        modifier: Modifier,
        panel: @Composable (BoxScope.() -> Unit),
    ) {
        placer(modifier, panel)
    }

}