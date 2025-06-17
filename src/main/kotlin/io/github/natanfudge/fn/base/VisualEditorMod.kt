@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.base

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.natanfudge.fn.compose.utils.mutableState
import io.github.natanfudge.fn.core.ComposePanelPlacer
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.FunState
import io.github.natanfudge.fn.physics.Visible
import io.github.natanfudge.fn.render.CameraMode
import io.github.natanfudge.fn.render.Tint

@Composable
fun ComposePanelPlacer.FunPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Panel(modifier) {
        MaterialTheme(darkColorScheme()) {
            content()
        }
    }
}

class VisualEditorMod(private val context: FunContext) : FunMod {
    private var mouseDownPos: Offset? = null

    private var hoveredObject: Visible? = null
    private var hoveredObjectOldTint: Tint? = null
    var selectedObject: Visible? by mutableStateOf(null)
    private var selectedObjectOldTint: Tint? = null

    @Composable
    fun FunEditor(fn: Fun) {
        Column(Modifier.padding(5.dp).width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
            val values = context.stateManager.getState(fn.id)
            if (values != null) {
                Text(fn.id.substringAfterLast("/"), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 30.sp)
                for ((key, value) in values.getCurrentState()) {
                    value as FunState<Any?>
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(key, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Box(Modifier.weight(1f))
                        value.editor.EditorUi(
                            mutableState(value.value) { value.value = it }
                        )
                        Box(Modifier.weight(1f))
                    }
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                for(child in fn.children) {
                    FunEditor(child)
                }
            }


        }

    }

    @Composable
    override fun ComposePanelPlacer.gui() {
        FunPanel(Modifier.align(Alignment.CenterEnd).padding(5.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))) {
                if (selectedObject?.data is Fun) {
                    FunEditor(selectedObject?.data as Fun)
                }
            }
        }
    }


    override fun handleInput(input: InputEvent) {
        if (input is InputEvent.PointerEvent) {
            if (input.eventType == PointerEventType.Move && (context.camera.mode == CameraMode.Orbital || context.camera.mode == CameraMode.Off)) {
                context.world.setCursorPosition(input.position)
            }

            colorHoveredObject()

            if (input.eventType == PointerEventType.Press) {
                mouseDownPos = input.position
            }
            captureSelectedObject(input)


        }
    }

    private fun captureSelectedObject(input: InputEvent.PointerEvent) {
        if (input.eventType == PointerEventType.Release) {
            val mouseDownPos = mouseDownPos ?: return
            // Don't reassign selected object if we dragged around too much
            if ((mouseDownPos - input.position).getDistanceSquared() < 100f) {
                val selected = context.world.hoveredObject as? Visible
                if (selectedObject != selected) {
                    // Restore the old color
                    selectedObject?.tint = selectedObjectOldTint ?: Tint(Color.White)
                    selectedObject = selected
                    // Save color to restore later
                    selectedObjectOldTint = hoveredObjectOldTint
                    if (selected != null && hoveredObjectOldTint != null) {
                        selected.tint = Tint(
                            lerp(
                                hoveredObjectOldTint!!.color,
                                Color.White.copy(alpha = 0.5f),
                                0.8f
                            ), strength = 0.8f
                        )
                    }
                }

            }
        }
    }

    private fun colorHoveredObject() {
        if (hoveredObject != context.world.hoveredObject) {
            // Restore the old color
            if (hoveredObject != selectedObject) {
                hoveredObject?.tint = hoveredObjectOldTint ?: Tint(Color.White)
            }

            val newHovered = (context.world.hoveredObject as? Visible)
            hoveredObject = newHovered
            // Save color to restore later
            hoveredObjectOldTint = newHovered?.tint
//            println("Setting tint to $hoveredObjectOldTint")
            if (newHovered != selectedObject) {
                newHovered?.tint = Tint(
                    lerp(hoveredObjectOldTint!!.color, Color.White.copy(alpha = 0.5f), 0.2f), strength = 0.2f
                )
            }
        }
    }
}