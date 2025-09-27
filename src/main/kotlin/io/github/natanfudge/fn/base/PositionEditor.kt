package io.github.natanfudge.fn.base

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.physics.RootTransformable
import io.github.natanfudge.fn.physics.TransformNode
import io.github.natanfudge.fn.physics.Transformable
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class SimpleArrow(
    val color: Color, id: FunId,
//    val transform: Transform,
    parent: Transformable = RootTransformable,
) : Fun(id) {
    val root by render(Model(Mesh.Cylinder, "ArrowCylinder"), parent)
    private val tip by render(Model(Mesh.arrowHead(PIf / 2.5f), "ArrowHead"), root)

    init {
        root.tint = Tint(color, 0.5f)
        tip.tint = Tint(color, 0.5f)
        tip.localTransform.translation = Vec3f(0f, 0f, 1.5f)
        tip.localTransform.rotation = Quatf.xRotation(PIf / 2)
        tip.localTransform.scale = Vec3f(2f, 2f, 2f)


    }


}


class PositionEditor(id: FunId, parent: Transformable = RootTransformable, pos: Vec3f) : Fun(id) {
    //TODO: Some future low-prio enhancements:
    // - 0. Make it so orbital movement with mouse is disabled when we are dragging an object.
    // - 1. Make the arrows always appear on the outside of the object, by considering its bounding box
    // - 2. Make the arrows always appear the same size independently of your distance from them.
    // - 3. When gripping something, have a grip cursor appear that is oriented in the axis we are going to move to.
    val root = TransformNode(
        Transform(
            translation = pos,
        ),
        parent
    )
    val x = SimpleArrow(
        Color.Red, id.child("x"),
        root
    )

    val y = SimpleArrow(
        Color.Green, id.child("y"),
        root
    )
    val z = SimpleArrow(
        Color.Blue, id.child("z"),
        root
    )

    private val arrows = listOf(x, y, z)

    private var heldArrow: FunRenderState? = null
    private var prevGripPos: Vec3f? = null

    init {
        // Make it point towards positive X
        x.root.localTransform.rotation = Quatf.yRotation(PIf / 2)
        // Make it point towards positive Y
        y.root.localTransform.rotation = Quatf.xRotation(PIf / -2)
        // Arrow already points towards positive Z by default


        x.root.localTransform.translation = Vec3f(0.5f, 0f, 0f)
        y.root.localTransform.translation = Vec3f(0f, 0.5f, 0f)
        z.root.localTransform.translation = Vec3f(0f, 0f, 0.5f)

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
                    val prevPos =  prevGripPos ?: return@listen
                    val newPos = getCursorPosOnPlane(arrow) ?: return@listen
                    val diff = newPos - prevPos
                    println(" Diff: $diff")
                    when (heldArrow?.parent) {
                        //TODO: this should move the parent transform instead and everything will move with it
                        x -> {
                            x.root.localTransform.translation = x.root.localTransform.translation.plusX(diff.x)
                        }

                        y -> {
                            y.root.localTransform.translation = y.root.localTransform.translation.plusY(diff.y)
                        }

                        z -> {
                            z.root.localTransform.translation = z.root.localTransform.translation.plusZ(diff.z)
                        }
                    }
                    prevGripPos = newPos
                }
            }

        }
    }

    private fun getAxis(arrow: FunRenderState) = when(arrow.parent) {
        x -> Vec3f(1f,0f,0f)
        y -> Vec3f(0f,1f,0f)
        z -> Vec3f(0f,0f,1f)
        else -> error("Expected arrow but got: ${arrow.parent}")
    }

    private fun getCursorPosOnPlane(obj: FunRenderState): Vec3f? {
        val pos = obj.transform.getTranslation()
        val dir = getAxis(obj)
        return renderer.getCursorPositionAlongCameraPlane(pos, dir)
    }

}




