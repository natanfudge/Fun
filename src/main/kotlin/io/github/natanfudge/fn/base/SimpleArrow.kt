package io.github.natanfudge.fn.base

import androidx.compose.ui.graphics.Color
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
    val transform: Transform,
    parent: Transformable = RootTransformable,
) : Fun(id) {
    val root = TransformNode(
        transform,
        parent
    )
    val cylinder by render(Model(Mesh.Cylinder, "ArrowCylinder"), root)
    private val tip by render(Model(Mesh.arrowHead(PIf / 2.5f), "ArrowHead"), root)

    init {
        cylinder.tint = Tint(color, 0.5f)
        tip.tint = Tint(color, 0.5f)
        tip.localTransform.translation = Vec3f(0f, 0f, 1f)
        tip.localTransform.rotation = Quatf.xRotation(PIf / 2)
//        tip.localTransform.scale = Vec3f(2f, 4f, 2f)


//                tip.localTransform.translation = Vec3f(0f, 0f, 2f)
//        tip.localTransform.rotation = Quatf()
        tip.localTransform.scale = Vec3f(1f, 1f, 1f)
    }
}

//TODO: there's definitely some dissonance in the way we propogate transforms in the transform tree, If I scale the parent in Z axis, the child should globally
// be scaled in the z axis, even if its rotated. Need to better understand this in general and see where we are getting it wrong, because right now
// the z axis scale is rotated and then it becomes like a x axis scale which mathemtically makes sense but isn't really what we want with "parent z scale"

class PositionEditor(id: FunId, parent: Transformable = RootTransformable, pos: Vec3f) : Fun(id) {
    val root = TransformNode(
        Transform(
            translation = pos,
            scale = Vec3f(1f, 1f, 5f) / 10f
        ),
        parent
    )
//    val x = SimpleArrow(
//        Color.Red, id.child("x"),
//        Transform(rotation = Quatf.xRotation(PIf / 2)),
//        root
//    )
//    val y = SimpleArrow(
//        Color.Green, id.child("y"),
//        Transform(rotation = Quatf.yRotation(PIf / 2)),
//        root
//    )
    val z = SimpleArrow(
        Color.Blue, id.child("z"),
//        Transform(rotation = Quatf.zRotation(PIf / 2)),
        Transform(),
        root
    )

//    init {
//        x.root.localTransform.rotation = Quatf.xRotation(PIf / 2)
//        y.root.localTransform.rotation = Quatf.yRotation(PIf / 2)
//        z.root.localTransform.rotation = Quatf.zRotation(PIf / 2)
//    }
}

//TODO: make orbital movement less trash, the issue is with the focal point I think

//TODO: visual editor should show the global transform of a renderobject

//TODO: visual editor should have a way to go up/down in the hierarchy, with an outline to go with it.

// TODO BUG: when we go from game selection to visual editor selection the gray marker remains on the selected block