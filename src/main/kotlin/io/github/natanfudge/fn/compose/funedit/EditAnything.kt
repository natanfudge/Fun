@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.compose.funedit

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.compose.utils.FloatField
import io.github.natanfudge.fn.compose.utils.mutableState
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.util.withValue
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

interface ValueEditor<T> {
    @Composable
    fun EditorUi(state: MutableState<T>, modifier: Modifier = Modifier)

    object Missing : ValueEditor<Any?> {
        @Composable
        override fun EditorUi(state: MutableState<Any?>, modifier: Modifier) {
            Text("No ValueEditor specified. This is a bug in the program. The current value is ${state.value}.", modifier)
        }

    }
}

interface ValueRenderer<in T> {
    @Composable
    fun Render(value: T, modifier: Modifier = Modifier)
}

val AABBEditor = VectorEditor(
    toMap = {mapOf("minX" to it.minX, "minY" to it.minY, "minZ" to it.minZ, "maxX" to it.maxX, "maxY" to it.maxY, "maxZ" to it.maxZ)},
    fromMap = {
        AxisAlignedBoundingBox(
            it.getValue("minX"), it.getValue("minY"), it.getValue("minZ"),
            it.getValue("maxX"), it.getValue("maxY"), it.getValue("maxZ")
        )
    },
    default = AxisAlignedBoundingBox.UnitAABB
)

val Vec3fEditor = VectorEditor(
    toMap = {mapOf("x" to it.x, "y" to it.y, "z" to it.z)},
    fromMap = { Vec3f(it.getValue("x"), it.getValue("y"), it.getValue("z"))},
    default = Vec3f.zero()
)

val QuatfEditor = VectorEditor(
    toMap = {mapOf("x" to it.x, "y" to it.y, "z" to it.z, "w" to it.w)},
    fromMap = { Quatf(it.getValue("x"), it.getValue("y"), it.getValue("z"), it.getValue("w"))},
    ranges = mapOf("x" to -1f..1f, "y" to -1f..1f, "z" to -1f..1f, "w" to -1f..1f),
    default = Quatf.identity()
)

//data class VectorComponent(val value: Float, val range: ClosedFloatingPointRange<Float>?)

class VectorEditor<T>(
    val toMap: (T) -> Map<String, Float>,
    val fromMap: (Map<String, Float>) -> T,
    val default: T,
    val ranges: Map<String, ClosedFloatingPointRange<Float>> = mapOf(),
) : ValueEditor<T> {
    @Composable
    override fun EditorUi(state: MutableState<T>, modifier: Modifier) {
        UntypedVectorEditor(
            ranges,
            mutableState(toMap(state.value)) { state.value = fromMap(it) },
            modifier
        )
    }
}

@Composable
fun UntypedVectorEditor(ranges: Map<String, ClosedFloatingPointRange<Float>?>, state: MutableState<Map<String, Float>>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for ((key, value) in state.value) {
            val range = ranges[key]
            FloatField(label = { Text(key) }, state = mutableState(value) {
                state.value = state.value.withValue(key, it)
            }, range = range, modifier = Modifier.width(70.dp))
        }
    }
}

