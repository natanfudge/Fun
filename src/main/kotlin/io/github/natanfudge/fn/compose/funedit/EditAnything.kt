@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.compose.funedit

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.natanfudge.fn.compose.utils.SimpleDropdownMenu
import io.github.natanfudge.fn.compose.utils.mutableState
import kotlin.reflect.KClass

interface ValueEditor<T> {
    @Composable
    fun Edit(value: T, onSetValue: (T) -> Unit, modifier: Modifier = Modifier)
}

interface ValueRenderer<in T> {
    @Composable
    fun Render(value: T, modifier: Modifier = Modifier)
}

object ToStringRenderer : ValueRenderer<Any?> {
    @Composable
    override fun Render(value: Any?, modifier: Modifier) {
        Text(value.toString(), modifier)
    }
}


class EditConfig(
    val editors: Map<KClass<*>, ValueEditor<*>>,
    val renderers: Map<KClass<*>, ValueRenderer<*>>,
)


@Composable
fun <T> EditAnything(
    editor: EditConfig,
    value: T, onSetValue: (T) -> Unit,
    kClass: KClass<T & Any>, possibleValues: List<T>?, label: String?,
    modifier: Modifier = Modifier,
) {
    if (possibleValues != null) {
        val renderer = editor.renderers[kClass] ?: ToStringRenderer
        renderer as ValueRenderer<T>
        val label = if (label == null) null else (@Composable {
            Text(label)
        });
        SimpleDropdownMenu(
            mutableState(value, onSetValue), possibleValues, text = { renderer.Render(value) },
            label = label, modifier
        )
    } else {
        val specificEditor = editor.editors[kClass]
        if (specificEditor != null) {
            (specificEditor as ValueEditor<T>).Edit(value, onSetValue, modifier)
        } else {
            Text("No ValueEditor specified for the type '$kClass'. This is a bug in the program. The current value is $value.", modifier)
        }
    }

}

