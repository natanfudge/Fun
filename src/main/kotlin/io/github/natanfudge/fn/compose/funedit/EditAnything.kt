@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.compose.funedit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.compose.utils.FloatField
import io.github.natanfudge.fn.compose.utils.mutableState
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Tint
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
    toMap = { mapOf("minX" to it.minX, "minY" to it.minY, "minZ" to it.minZ, "maxX" to it.maxX, "maxY" to it.maxY, "maxZ" to it.maxZ) },
    fromMap = {
        AxisAlignedBoundingBox(
            it.getValue("minX"), it.getValue("minY"), it.getValue("minZ"),
            it.getValue("maxX"), it.getValue("maxY"), it.getValue("maxZ")
        )
    },
    default = AxisAlignedBoundingBox.UnitAABB
)

val ColorEditor = VectorEditor(
    toMap = { it.toMap() },
    fromMap = { colorFrom(it) },
    default = Color.White
)

private fun Color.toMap() = mapOf("red" to red, "green" to green, "blue" to blue, "alpha" to alpha)
private fun colorFrom(map: Map<String, Float>) = Color(
    map.getValue("red"), map.getValue("green"),
    map.getValue("blue"), map.getValue("alpha")
)

val TintEditor = VectorEditor(
    toMap = { it.color.toMap() + mapOf("strength" to it.strength) },
    fromMap = { Tint(colorFrom(it), it.getValue("strength")) },
    default = Tint(Color.White, 0f),
    componentConfig = mapOf(
        "red" to VectorComponentConfig(range = 0f..1f),
        "green" to VectorComponentConfig(range = 0f..1f),
        "blue" to VectorComponentConfig(range = 0f..1f),
        "alpha" to VectorComponentConfig(range = 0f..1f),
        "strength" to VectorComponentConfig(range = 0f..1f, width = 100.dp)
    )
)

object BooleanEditor : ValueEditor<Boolean> {
    @Composable
    override fun EditorUi(state: MutableState<Boolean>, modifier: Modifier) {
        Checkbox(checked = state.value, onCheckedChange = { state.value = it }, modifier)
    }

}

val Vec3fEditor = VectorEditor(
    toMap = {
        mapOf("x" to it.x, "y" to it.y, "z" to it.z)
    },
    fromMap = { Vec3f(it.getValue("x"), it.getValue("y"), it.getValue("z")) },
    default = Vec3f.zero()
)

val QuatfEditor = VectorEditor(
    toMap = { mapOf("x" to it.x, "y" to it.y, "z" to it.z, "w" to it.w) },
    fromMap = { Quatf(it.getValue("x"), it.getValue("y"), it.getValue("z"), it.getValue("w")) },
    componentConfig = mapOf(
        "x" to VectorComponentConfig(range = -1f..1f),
        "y" to VectorComponentConfig(range = -1f..1f),
        "z" to VectorComponentConfig(range = -1f..1f),
        "w" to VectorComponentConfig(range = -1f..1f),
    ),
    default = Quatf.identity()
)

//data class VectorComponent(val value: Float, val range: ClosedFloatingPointRange<Float>?)

class VectorEditor<T>(
    val toMap: (T) -> Map<String, Float>,
    val fromMap: (Map<String, Float>) -> T,
    val default: T,
    val componentConfig: Map<String, VectorComponentConfig> = mapOf(),
) : ValueEditor<T> {
    @Composable
    override fun EditorUi(state: MutableState<T>, modifier: Modifier) {
        UntypedVectorEditor(
            componentConfig,
            mutableState(toMap(state.value)) { state.value = fromMap(it) },
            modifier
        )
    }
}

data class VectorComponentConfig(
    val range: ClosedFloatingPointRange<Float>? = null,
    val width: Dp = 70.dp,
)

@Composable
fun UntypedVectorEditor(ranges: Map<String, VectorComponentConfig>, state: MutableState<Map<String, Float>>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for ((key, value) in state.value) {
            val config = ranges.getOrDefault(key, VectorComponentConfig())
            FloatField(label = { Text(key) }, state = mutableState(value) {
                state.value = state.value.withValue(key, it)
            }, range = config.range, modifier = Modifier.width(config.width))
        }
    }
}

