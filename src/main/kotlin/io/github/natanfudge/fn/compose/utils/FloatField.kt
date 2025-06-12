package io.github.natanfudge.fn.compose.utils

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FloatField(
    state: MutableState<Float>,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = state.value.toString(),
        onValueChange = { new ->
            val newFloat  = new.toFloatOrNull()
            if (newFloat != null) {
                state.value = (newFloat.coerceIn(range))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.widthIn(1.dp, Dp.Infinity),
//             1) turn off Material's 280 dp × 56 dp minimum
//            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
//             2) size to the intrinsic content
//            .width(IntrinsicSize.Min)        // width = longest line of text
//            .height(IntrinsicSize.Min)       // height = actual line height(s)
    )


}

fun main() {
    composeApp {
        FloatField(mutableStateOf(0f))

    }
}

