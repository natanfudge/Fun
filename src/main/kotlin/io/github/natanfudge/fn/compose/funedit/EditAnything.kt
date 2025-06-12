@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.compose.funedit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.compose.utils.FloatField
import io.github.natanfudge.fn.compose.utils.mutableState
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.util.withValue
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
            }, range = range, modifier = Modifier.width(100.dp))
        }
    }
}


//object Text
//
//object ToStringRenderer : ValueRenderer<Any?> {
//    @Composable
//    override fun Render(value: Any?, modifier: Modifier) {
//        Text(value.toString(), modifier)
//    }
//}
//
//
//class GlobalEditConfig(
//    val editors: Map<KClass<*>, ValueEditor<*>>,
//    val renderers: Map<KClass<*>, ValueRenderer<*>>,
//)


//data class FieldEditConfig<T>(
//    val kClass: KClass<T & Any>,
//    val possibleValues: List<T>?,
//    val label: String?
//)

//
//@Composable
//fun <T> EditAnything(
//
//    globalConfig: GlobalEditConfig,
//    editConfig: FieldEditConfig<T>,
//    value: T, onSetValue: (T) -> Unit,
//    modifier: Modifier = Modifier,
//) {
//    val kClass = editConfig.kClass
//    val possibleValues = editConfig.possibleValues
//    val label = editConfig.label
//    if (possibleValues != null) {
//        val renderer = globalConfig.renderers[kClass] ?: ToStringRenderer
//        renderer as ValueRenderer<T>
//        val label = if (label == null) null else (@Composable {
//            Text(label)
//        });
//        SimpleDropdownMenu(
//            mutableState(value, onSetValue), possibleValues, text = { renderer.Render(value) },
//            label = label, modifier
//        )
//    } else {
//        val specificEditor = globalConfig.editors[kClass]
//        if (specificEditor != null) {
//            (specificEditor as ValueEditor<T>).Edit(value, onSetValue, editConfig, modifier)
//        } else {
//            Text("No ValueEditor specified for the type '$kClass'. This is a bug in the program. The current value is $value.", modifier)
//        }
//    }
//
//}

