package io.github.natanfudge.fn.base

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunMod
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.physics.FunRenderState
import io.github.natanfudge.fn.physics.Tag
import io.github.natanfudge.fn.render.Tint

class HoverHighlightMod(
    val context: FunContext,
    /**
     * Allows making another Fun be hovered instead of the one the cursor is pointing at.
     * Return null to not highlight anything.
     */
    private val redirectHover: (Fun) -> Fun? = { it },
    private val hoverRenderPredicate: (FunRenderState) -> Boolean = { true },
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

    override fun frame(deltaMs: Double) {
        colorHoveredObject()
    }

    var hoveredObjectRoot: Fun? = null
        private set

    private fun colorHoveredObject() {
        val newRoot = context.getHoveredRoot()
        val actualNewRoot = if (newRoot == null) null else redirectHover(newRoot)

        if (actualNewRoot != hoveredObjectRoot) {
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

            actualNewRoot?.forEachChildTyped<FunRenderState> {
                // Only apply hover tint when it is allowed
                if (hoverRenderPredicate(it)) {
                    // Save old tint
                    it.setTag(PreHoverTintTag, it.tint)
                    // Apply new tint
                    it.tint = Tint(
                        lerp(it.tint.color, Color.White.copy(alpha = 0.5f), 0.2f), strength = 0.1f
                    )
                    // Remember new tint
                    it.setTag(PostHoverTintTag, it.tint)
                }
            }

        }
        hoveredObjectRoot = actualNewRoot

    }

}