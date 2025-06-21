package io.github.natanfudge.fn.base

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.FunRenderState
import io.github.natanfudge.fn.physics.Tag
import io.github.natanfudge.fn.render.Tint

class HoverHighlightMod(
    val context: FunContext,
    /**
     * Allows specifying which items should be hoverable. Return false to not allow an item to be hoverable.
     */
    private val hoverPredicate: (Fun) -> Boolean = { true },
) : FunMod {
    companion object {
        /**
         * Add this tag to an object to prevent it from being highlighted when hovered.
         */
        val DoNotHighlightTag = Tag<Boolean>("HoverHighlightMod-DoNotHighlight")

        // The tint the object had before hovering over it, to reset to it
        val PreHoverTintTag = Tag<Tint>("HoverHighlightMod-PreHoverTint")

        // The tint that was given to the object by hovering over it. We store this to check if this has changed, and if so
        val PostHoverTintTag = Tag<Tint>("HoverHighlightMod-PostHoverTint")
    }

    var hoveredObjectRoot: Fun? = null
        private set

    private fun colorHoveredObject() {
        val newRoot = context.getHoveredRoot()

        val canHover = if (newRoot == null) false else hoverPredicate(newRoot)

        if (newRoot != hoveredObjectRoot) {
            // We no longer hover over the old object, remove its tint
            if (hoveredObjectRoot != null) {
                val oldHover = hoveredObjectRoot!!
                oldHover.forEachChildTyped<FunRenderState> {
                    if (it.tint == it.getTag(PostHoverTintTag)) {
                        // If the tint has not changed since hovering...
                        // Restore the old tint
                        it.tint = it.getTag(PreHoverTintTag) ?: error("Missing PreHoverTintTag on FunRenderState that does have PostHoverTag")
                    }
                    it.removeTag(PostHoverTintTag)
                    it.removeTag(PreHoverTintTag)
                }
            }

            if (canHover) {
                newRoot?.forEachChildTyped<FunRenderState> {
                    // Save old tint
                    it.setTag(PreHoverTintTag, it.tint)
                    // Apply new tint
                    it.tint = Tint(
                        lerp(it.tint.color, Color.White.copy(alpha = 0.5f), 0.2f), strength = 0.2f
                    )
                    // Remember new tint
                    it.setTag(PostHoverTintTag, it.tint)
                }
            }
        }
        if (canHover) {
            hoveredObjectRoot = newRoot
        } else {
            hoveredObjectRoot = null
        }
    }

    override fun handleInput(input: InputEvent) {
        if (input is InputEvent.PointerEvent) {
            colorHoveredObject()
        }
    }
}