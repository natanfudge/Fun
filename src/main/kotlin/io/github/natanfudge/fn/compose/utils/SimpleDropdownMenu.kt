package io.github.natanfudge.fn.compose.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp




/**
 * Wraps [ExposedDropdownMenuBox] in an actually reasonable API. Just a simple dropdown menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SimpleDropdownMenu(
    value: MutableState<T>,
    options: List<T>,
    text: @Composable (T) -> Unit = { Text(it.toString()) },
    label: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // DisableSelection to make clicking it not awkward
    DisableSelection {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier
        ) {
            Column {
                Column() {
                    Row(
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 15.dp, vertical = if (label == null) 15.dp else 5.dp)
                            .clip(RoundedCornerShape(0.dp, 8.dp, 0.dp, 0.dp))
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            if (label != null) {
                                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    label()
                                }
                            }
                            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)) {
                                text(value.value)
                            }
                        }

                        val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

                        Icon(
                            Icons.Filled.ArrowDropDown,
                            null,
                            Modifier.rotate(arrowRotation),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onBackground)
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { text(selectionOption) },
                        onClick = {
                            value.value = selectionOption
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,

                        )
                }
            }
        }
    }
}
