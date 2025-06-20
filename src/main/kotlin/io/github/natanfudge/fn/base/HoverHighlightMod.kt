package io.github.natanfudge.fn.base

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.physics.Tag
import io.github.natanfudge.fn.physics.Visible
import io.github.natanfudge.fn.render.Tint

class HoverHighlightMod(val context: FunContext) : FunMod {
    companion object {
        /**
         * Add this tag to an object to prevent it from being highlighted when hovered.
         */
        val DoNotHighlightTag = Tag<Boolean>("HoverHighlightMod-DoNotHighlight")
    }

    var hoveredObject: Visible? = null
        private set

    // We store this to track if the object's tint has changed since hovering over it, in which case we don't touch the tint was hover is removed,
    // to not overwrite the tint that was applied to the object after hovering.
    private var hoveredObjectHoverTint: Tint? = null
    var hoveredObjectOldTint: Tint? = null
        private set

    private fun colorHoveredObject() {
        // If hover target has changed...
        if (hoveredObject != context.world.hoveredObject) {
            // Restore the old color, but only if not was has changed the tint since hovering.
            if (hoveredObject?.tint == hoveredObjectHoverTint) {
                hoveredObject?.tint = hoveredObjectOldTint ?: Tint(Color.White)
            }

            val newHovered = context.world.hoveredObject ?: return
            check(newHovered is Visible) {"Expected hoveredObject to be Visible, but was ${context.world.hoveredObject?.javaClass?.name ?: "null"} instead."}
            hoveredObject = newHovered
            // Save color to restore later
            hoveredObjectOldTint = newHovered.tint
            if (newHovered.getTag(DoNotHighlightTag) != true) {
                val newTint = Tint(
                    lerp(hoveredObjectOldTint!!.color, Color.White.copy(alpha = 0.5f), 0.2f), strength = 0.2f
                )
                newHovered.tint = newTint
                hoveredObjectHoverTint = newTint
            }
        }
    }

    override fun handleInput(input: InputEvent) {
        if (input is InputEvent.PointerEvent) {
            colorHoveredObject()
        }
    }
}