package io.github.natanfudge.fn.base

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.fn.util.average
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class EditPositionArrow(
    val color: Color, id: FunId,
    parent: Fun,
//    initialPos: Vec3f
//    val transform: Transform,
//    parent: Transformable = RootTransformable,
) : Fun(parent.id.child(id), parent) {
    val root by render(Model(Mesh.Cylinder, "ArrowCylinder"))
    private val tip by render(Model(Mesh.arrowHead(PIf / 2.5f), "ArrowHead"), root)

    init {
        root.tint = Tint(color, 0.5f)
        tip.tint = Tint(color, 0.5f)
        tip.localTransform.translation = Vec3f(0f, 0f, 1.5f)
        tip.localTransform.rotation = Quatf.xRotation(PIf / 2)
        tip.localTransform.scale = Vec3f(2f, 2f, 2f)

        // Don't allow selecting the arrows themselves
        root.setTag(VisualEditor.CannotBeVisuallyEditedTag, Unit)
        tip.setTag(VisualEditor.CannotBeVisuallyEditedTag, Unit)

    }


}


class VisualPositionEditor(
    id: FunId, val target: FunRenderState,
    /**
     * Called when a given movement is requested by the position editor. Should be used to move the target object.
     */
    onMove: (Vec3f) -> Unit,
) : Fun(id) {

    private fun getTargetCenter() = target.boundingBox.getCenter()


    val x = EditPositionArrow(
        Color.Red, "x" ,this,
    )

    val y = EditPositionArrow(
        Color.Green, "y", this
    )
    val z = EditPositionArrow(
        Color.Blue, "z", this,
    )

    private val arrows = listOf(x, y, z)


    init {
        updateArrowPositions()
        target.afterTransformChange {
            updateArrowPositions()
        }.closeWithThis()
    }


    private fun updateArrowPositions() {
        for (arrow in arrows) {
            arrow.root.localTransform.translation = getTargetCenter()
        }

        val arrowsDistance = average(target.boundingBox.width, target.boundingBox.height, target.boundingBox.depth)
        x.root.localTransform.translation += Vec3f(arrowsDistance, 0f, 0f)
        y.root.localTransform.translation += Vec3f(0f, arrowsDistance, 0f)
        z.root.localTransform.translation += Vec3f(0f, 0f, arrowsDistance)
    }

    private var heldArrow: FunRenderState? = null
    private var prevGripPos: Vec3f? = null

    init {
        // Make it point towards positive X
        x.root.localTransform.rotation = Quatf.yRotation(PIf / 2)
        // Make it point towards positive Y
        y.root.localTransform.rotation = Quatf.xRotation(PIf / -2)
        // Arrow already points towards positive Z by default




        for (arrow in arrows) {
            arrow.root.localTransform.scale = Vec3f(1f, 1f, 5f) / 10f
        }

        events.pointer.listen { input ->
            when (input.eventType) {
                PointerEventType.Press -> {
                    val obj = renderer.hoveredObject as? FunRenderState ?: return@listen
                    // Make sure to only move one of the 3 arrows.
                    if (obj.parent in arrows) {
                        // Hold Arrow
                        heldArrow = obj
                        prevGripPos = getCursorPosOnPlane(obj)
                    }
                }

                PointerEventType.Release -> {
                    heldArrow = null
                    prevGripPos = null
                }

                PointerEventType.Move -> {
                    // Drag arrow
                    val arrow = heldArrow ?: return@listen
                    val prevPos = prevGripPos ?: return@listen
                    val newPos = getCursorPosOnPlane(arrow) ?: return@listen
                    val diff = newPos - prevPos
                    println(" Diff: $diff")
                    when (heldArrow?.parent) {

                        // Drag ONLY across the axis of the arrow
                        x -> {
                            onMove(Vec3f(diff.x, 0f, 0f))
                        }

                        y -> {
                            onMove(Vec3f(0f, diff.y, 0f))
                        }

                        z -> {
                            onMove(Vec3f(0f, 0f, diff.z))
                        }
                    }
                    prevGripPos = newPos
                }
            }

        }
    }

    private fun getAxis(arrow: FunRenderState) = when (arrow.parent) {
        x -> Vec3f(1f, 0f, 0f)
        y -> Vec3f(0f, 1f, 0f)
        z -> Vec3f(0f, 0f, 1f)
        else -> error("Expected arrow but got: ${arrow.parent}")
    }

    private fun getCursorPosOnPlane(obj: FunRenderState): Vec3f? {
        val pos = obj.transform.getTranslation()
        val dir = getAxis(obj)
        return renderer.getCursorPositionAlongCameraPlane(pos, dir)
    }

}




