package io.github.natanfudge.fn.compose.utils

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

@Composable
fun IntField(
    state: MutableState<Int>,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    range: ClosedFloatingPointRange<Int>? = null,
) {
    OutlinedTextField(
        label = label,
        value = state.value.toString(),
        onValueChange = { new ->
            val newFloat = new.toIntOrNull()
            if (newFloat != null) {
                state.value = if (range == null) newFloat else newFloat.coerceIn(range)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.widthIn(1.dp, Dp.Infinity) // Reduce the default width as much as possible
    )
}

