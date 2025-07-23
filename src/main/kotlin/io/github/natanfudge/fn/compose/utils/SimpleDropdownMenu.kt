package io.github.natanfudge.fn.compose.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application


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
    DropdownMenuMatchingContentWidth(options, onSelectItem = {
        value.value = it
    }, text, modifier) { expanded ->
        Column {
            Row(
                Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 15.dp, vertical = if (label == null) 15.dp else 5.dp)
                    .clip(RoundedCornerShape(0.dp, 8.dp, 0.dp, 0.dp)),
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownMenuMatchingContentWidth(
    options: List<T>,
    onSelectItem: (T) -> Unit,
    text: @Composable (T) -> Unit = { Text(it.toString()) },
    modifier: Modifier = Modifier,
    content: @Composable (expanded: Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // DisableSelection to make clicking it not awkward
    DisableSelection {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier
        ) {
            Box(
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            ) {
                content(expanded)
            }


            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { text(selectionOption) },
                        onClick = {
                            onSelectItem(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> StandaloneWidthDropdownMenu(
    options: List<T>,
    onSelectItem: (T) -> Unit,
    text: @Composable (T) -> Unit = { Text(it.toString()) },
    modifier: Modifier = Modifier,
    content: @Composable (expanded: Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    //DisableSelection keeps the anchor free of text‑selection handles, just
    //like in the original version.
    DisableSelection {
        //The Box is both the visual anchor and the touch target.
        Box(
            modifier
                //Makes the whole anchor clickable to toggle the dropdown.
                .clickable { expanded = !expanded }
        ) {
            //Caller‑supplied UI (e.g. an OutlinedTextField or any custom view)
            content(expanded)
        }

        //Regular DropdownMenu ‑‑ width = max(intrinsic width of items)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { text(option) },
                    onClick = {
                        onSelectItem(option)
                        expanded = false
                    },
                    //Keeps the default M3 padding to match Exposed styling
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
            // This is actually vital, if we don't add this it screws up recomposition when there are no items, and it will
            // refuse to draw the items, even if the list of options fills up later.
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("[No Items]") },
                    onClick = {}
                )
            }
        }
    }
}


fun main() {
    application {
        Window(::exitApplication) {
            ComposeLayerDebug()
        }
    }
}

@Composable
fun ComposeLayerDebug() {
    Popup {
        Text("Halo!")
    }
}