@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.compose.funedit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

interface ValueEditor<T> {
    @Composable
    fun EditorUi(value: T, onSetValue: (T) -> Unit, modifier: Modifier = Modifier)

    object Missing : ValueEditor<Any?> {
        @Composable
        override fun EditorUi(value: Any?, onSetValue: (Any?) -> Unit, modifier: Modifier) {
            Text("No ValueEditor specified. This is a bug in the program. The current value is $value.", modifier)
        }

    }
}

interface ValueRenderer<in T> {
    @Composable
    fun Render(value: T, modifier: Modifier = Modifier)
}

class VectorEditor<T>(val toMap: (T) -> Map<String, Float>, val fromMap: (Map<String, Float>) -> T, val default: T) : ValueEditor<T> {
    @Composable
    override fun EditorUi(value: T, onSetValue: (T) -> Unit, modifier: Modifier) {

    }
}

@Composable fun NamedNumbersEditor(value: MutableState<Map<String, Float>>) {
    Row {

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

