@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.base

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.natanfudge.fn.compose.utils.mutableState
import io.github.natanfudge.fn.core.*
import io.github.natanfudge.fn.core.FunOld
import io.github.natanfudge.fn.network.state.FunState
import io.github.natanfudge.fn.network.state.listenAsState
import io.github.natanfudge.fn.render.FunRenderObject
import io.github.natanfudge.fn.render.FunRenderState
import io.github.natanfudge.fn.render.Tint

fun FunContext.addFunPanel(modifier: BoxScope. () -> Modifier = { Modifier }, content: @Composable BoxScope.() -> Unit) {
    gui.addPanel(modifier) {
        MaterialTheme(darkColorScheme()) {
            content()
        }
    }
}


@Composable
fun ComposePanelPlacer.FunPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Panel(modifier) {
        MaterialTheme(darkColorScheme()) {
            content()
        }
    }
}

/**
 * If you use [HoverHighlight], you should pass it in this constructor, otherwise pass the [FunContext] and it will be created internally for this [VisualEditor].
 */
class VisualEditor(
    private val hoverMod: HoverHighlight, private val inputManagerMod: InputManager,
    /**
     * Whether the visual editor will be enabled by default. Note that the visual Editor can still be toggled by using the "Toggle Visual Editor" hotkey.
     */
     var enabled: Boolean = true,
)  {
    constructor(app: FunApp, inputManagerMod: InputManager, enabled: Boolean = true) : this(
        HoverHighlight(app.context), inputManagerMod, enabled
    )

    init {
        inputManagerMod.registerHotkey("Toggle Visual Editor", Key.V, onRelease = {
            enabled = !enabled
            println("Visual Editor=$enabled")
            if (!enabled) {
                restoreOldTint()
                // Reset state if disabled
                selectedObject = null
                selectedObjectOldTint = null
                mouseDownPos = null
            }
        })
        hoverMod.context.addFunPanel({ Modifier.align(Alignment.CenterEnd).padding(5.dp) }) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))) {
                val root = selectedObject?.getRoot()
                if (root != null) {
                    Column(
                        Modifier.padding(5.dp).width(IntrinsicSize.Max)
                            .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FunEditor(root)
                    }
                }
            }
        }
        hoverMod.context.events.input.listenPermanently { input ->
            if (input is InputEvent.PointerEvent && enabled) {
                if (input.eventType == PointerEventType.Press) {
                    mouseDownPos = input.position
                }
                captureSelectedObject(input)
            }
        }
    }

    private val context = hoverMod.context
    private var mouseDownPos: Offset? = null
    var selectedObject: FunRenderObject? by mutableStateOf(null)
    private var selectedObjectOldTint: Tint? = null

    @Composable
    fun FunEditor(fn: FunOld) {
        val values = context.stateManager.getState(fn.id)
        if (values != null) {
            Text(fn.id.substringAfterLast("/"), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 30.sp)
            for ((key, state) in values.getCurrentState()) {
                state as FunState<Any?>
                val value by state.listenAsState()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(key, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Box(Modifier.weight(1f))
                    state.editor.EditorUi(
                        mutableState(value) { state.value = it }
                    )
                    Box(Modifier.weight(1f))
                }
            }
        }
        for (child in fn.children) {
            FunEditor(child)
        }
    }

    private fun restoreOldTint() {
        selectedObject?.tint = selectedObjectOldTint ?: Tint(Color.White)
    }

    private fun captureSelectedObject(input: InputEvent.PointerEvent) {
        if (input.eventType == PointerEventType.Release) {
            val mouseDownPos = mouseDownPos ?: return
            // Don't reassign selected object if we dragged around too much
            if ((mouseDownPos - input.position).getDistanceSquared() < 100f) {
                val selected = context.world.hoveredObject as? FunRenderObject
                if (selectedObject != selected) {
                    // Restore the old color
                    restoreOldTint()
                    // We can hover-highlight again
                    selectedObject?.removeTag(HoverHighlight.DoNotHighlightTag)
                    selectedObject = selected
                    // Save color to restore later
                    selectedObjectOldTint = selected?.getTag(HoverHighlight.PreHoverTintTag) ?: selected?.tint
                    val oldTint = selectedObjectOldTint
                    if (selected != null) {
                        // Don't hover-highlight when selecting
                        selected.setTag(HoverHighlight.DoNotHighlightTag, true)
                        selected.tint = Tint(
                            lerp(
                                oldTint!!.color,
                                Color.White.copy(alpha = 0.5f),
                                0.8f
                            ), strength = 0.6f
                        )
                    }
                }

            }
        }
    }


}