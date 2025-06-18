package io.github.natanfudge.fn.compose.utils

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.natanfudge.fn.compose.funedit.Vec3fEditor
import io.github.natanfudge.wgpu4k.matrix.Vec3f

@Composable
fun FloatField(
    state: MutableState<Float>,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    range: ClosedFloatingPointRange<Float>? = null,
) {
    OutlinedTextField(
        label = label,
        value = state.value.toString(),
        onValueChange = { new ->
            val newFloat = new.toFloatOrNull()
            if (newFloat != null) {
                state.value = if (range == null) newFloat else newFloat.coerceIn(range)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.widthIn(1.dp, Dp.Infinity) // Reduce the default width as much as possible
    )
}

fun main() {
    composeApp {
//        FloatField(mutableStateOf(0f))
        val state = remember { mutableStateOf(Vec3f.zero()) }
        Vec3fEditor.EditorUi(
            state, Modifier.padding(5.dp)
        )
        println(state.value)

//        UntypedVectorEditor(
//            mutableMapOf(),
//            mutableStateOf(
//                mutableMapOf(
//                    "x" to 123f,
//                    "y" to 3.2f,
//                    "z" to -1f
//                )
//            ),
//            Modifier.padding(5.dp)
//        )

    }
}

