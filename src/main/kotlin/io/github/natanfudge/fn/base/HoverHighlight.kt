package io.github.natanfudge.fn.base

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunOld
import io.github.natanfudge.fn.core.Tag
import io.github.natanfudge.fn.render.FunRenderObject
import io.github.natanfudge.fn.render.FunRenderState
import io.github.natanfudge.fn.render.Tint

class HoverHighlight(
    val context: FunContext,
    /**
     * Allows making another Fun be hovered instead of the one the cursor is pointing at.
     * Return null to not highlight anything.
     */
    private val redirectHover: (FunOld) -> FunOld? = { it },
    /**
     * Allows filtering out specific [FunRenderStateOld]s, subcomponents of [FunOld]s, from being highlighted.
     * Note that [redirectHover] must still return non-null for this to matter.
     */
    private val hoverRenderPredicate: (FunRenderObject) -> Boolean = { true },
) {
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

    init {
        context.events.frame.listenPermanently {
            colorHoveredObject()
        }
    }

    var hoveredObjectRoot: FunOld? = null
        private set

    private fun colorHoveredObject() {
        val newRoot = context.getHoveredRoot()
//        println("Hovered: ${newRoot?.id}")
        val actualNewRoot = if (newRoot == null) null else redirectHover(newRoot)

        if (actualNewRoot != hoveredObjectRoot) {
            // We no longer hover over the old object, remove its tint
            if (hoveredObjectRoot != null) {
                val oldHover = hoveredObjectRoot!!

//                oldHover.forEachChildTyped<FunRenderStateOld> {
//                    if (it.tint == it.getTag(PostHoverTintTag)) {
//                        // If the tint has not changed since hovering...
//                        // Restore the old tint
//                        it.tint = it.getTag(PreHoverTintTag) ?: error("Missing PreHoverTintTag on FunRenderState that does have PostHoverTag")
//                    }
//                    it.removeTag(PostHoverTintTag)
//                    it.removeTag(PreHoverTintTag)
//                }

                oldHover.forEachChildTyped<FunRenderObject> {
                    if (it.tint == it.getTag(PostHoverTintTag)) {
                        // If the tint has not changed since hovering...
                        // Restore the old tint
                        it.tint = it.getTag(PreHoverTintTag) ?: error("Missing PreHoverTintTag on FunRenderState that does have PostHoverTag")
                    }
                    it.removeTag(PostHoverTintTag)
                    it.removeTag(PreHoverTintTag)
                }
            }

//            actualNewRoot?.forEachChildTyped<FunRenderStateOld> {
//                // Only apply hover tint when it is allowed
//                if (hoverRenderPredicate(it)) {
//                    // Save old tint
//                    it.setTag(PreHoverTintTag, it.tint)
//                    // Apply new tint
//                    it.tint = Tint(
//                        lerp(it.tint.color, Color.White.copy(alpha = 0.5f), 0.2f), strength = 0.1f
//                    )
//                    // Remember new tint
//                    it.setTag(PostHoverTintTag, it.tint)
//                }
//            }

            actualNewRoot?.forEachChildTyped<FunRenderObject> {
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