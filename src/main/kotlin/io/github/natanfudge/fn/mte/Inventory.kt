package io.github.natanfudge.fn.mte

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.natanfudge.fn.base.addFunPanel
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.network.state.funList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import natan.`fun`.generated.resources.Res
import java.net.URI
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.toPath
import kotlin.math.min


class Inventory(val game: DeepSoulsGame) : Fun("Inventory") {
    val maxSlots = 15

    private val items = funList<Item>("items", List(maxSlots) {
        Item(ItemType.Nothing, 0)
    }.toMutableStateList())


    private val maxStackSize = 10

    /**
     * Returns the leftover count of the item that could not be inserted
     */
    fun insert(item: Item): Int {
        var remainder = merge(item)
        if (remainder > 0) {
            remainder = addToEmptySlots(item.copy(count = remainder))
        }
        return remainder
    }

    private fun addToEmptySlots(item: Item): Int {
        var remainder = item.count
        for ((i, invItem) in items.withIndex()) {
            if (invItem.type == ItemType.Nothing) {
                val transfer = min(remainder, maxStackSize)
                items[i] = item.copy(count = invItem.count + transfer)
                remainder -= transfer
                if (remainder == 0) return 0
            }
        }
        return remainder
    }

    /**
     * Attempts to merge the given [item] stack with existing stacks in the inventory.
     * Returns the leftover count of the item that could not be merged with existing item stacks
     */
    private fun merge(item: Item): Int {
        var remainder = item.count
        for ((i, invItem) in items.withIndex()) {
            if (invItem.type == item.type) {
                val possibleTransfer = maxStackSize - invItem.count
                if (possibleTransfer > 0) {
                    val transfer = min(remainder, possibleTransfer)
                    items[i] = invItem.copy(count = invItem.count + transfer)
                    remainder -= transfer
                    if (remainder == 0) return 0
                }
            }
        }
        return remainder
    }


    val itemsPerRow = 10
    private val slotWidth = 60.dp

    init {
        game.context.addFunPanel({ Modifier.align(Alignment.BottomCenter) }) {
            val slotBorder = 2.dp
            val totalSlotWidth = slotWidth
            val inventoryWidth = (slotBorder + totalSlotWidth) * itemsPerRow - slotBorder // We have one less border
            Card(Modifier.width(width = inventoryWidth).heightIn(min = 54.dp)) {
                Column(Modifier.height(IntrinsicSize.Min)) {
                    for ((rowI, row) in items.chunked(itemsPerRow).withIndex()) {
                        if (rowI > 0) {
                            HorizontalDivider()
                        }
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            for (item in row) {
                                DisplayItem(item)
                                VerticalDivider(thickness = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DisplayItem(item: Item) {
        if (item.type == ItemType.Nothing) {
            Box(Modifier.size(slotWidth))
            return
        }
        Box(Modifier.size(slotWidth)) {
            val state = rememberResourceImageState("files/icons/items/${item.type.name.lowercase()}.png")

            ResourceImage(state, Modifier.size(slotWidth))

            if (state == ImageLoadState.NotFound) {
                Text(item.type.toString(), modifier = Modifier.padding(5.dp))
            }

            if (state is ImageLoadState.StartedDisplay) {
                Text(item.count.toString(), Modifier.align(Alignment.BottomEnd).padding(5.dp))
            }
        }

    }
}

sealed interface ImageLoadState {
    object CalculatingUri : ImageLoadState
    object NotFound : ImageLoadState

    interface StartedDisplay : ImageLoadState {
        val uri: String
    }

    // Not exposing anything past Loading for now, this is good enough since Loading means the image has actually partially showed up
    class Loading(override val uri: String) : StartedDisplay
    class Error(override val uri: String) : StartedDisplay
    class Empty(override val uri: String) : StartedDisplay
    class Success(override val uri: String) : StartedDisplay
}

// Cache resource uris because resolving them takes a lot of time
private val resourceUriCache = mutableMapOf<String, String>()

@Composable
fun rememberResourceImageState(path: String): ImageLoadState {
    var stage: ImageLoadState by remember(path) {
        val uri = resourceUriCache[path]
        val state = if (uri != null) ImageLoadState.Loading(uri)
        else ImageLoadState.CalculatingUri
        mutableStateOf(state)
    }
    if (stage == ImageLoadState.CalculatingUri) {
        LaunchedEffect(path) {
            val uri = getResourceUri(path)
            if (uriExists(uri)) {
                val coilUri = uriToCoil(uri)
                resourceUriCache[path] = coilUri
                stage = ImageLoadState.Loading(coilUri)
            } else {
                stage = ImageLoadState.NotFound
            }
        }
    }
    return stage
}



@Composable
fun ResourceImage(
    state: ImageLoadState,
    modifier: Modifier = Modifier,
    onState: (ImageLoadState) -> Unit = {},
    contentDescription: String? = null,
) {
    if (state is ImageLoadState.StartedDisplay) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(state.uri)
                .crossfade(false)
                .build(),
            contentDescription = contentDescription,
            modifier,
            onState = {
                onState(
                    when (it) {
                        AsyncImagePainter.State.Empty -> ImageLoadState.Empty(state.uri)
                        is AsyncImagePainter.State.Error -> ImageLoadState.Error(state.uri)
                        is AsyncImagePainter.State.Loading -> ImageLoadState.Loading(state.uri)
                        is AsyncImagePainter.State.Success -> ImageLoadState.Success(state.uri)
                    }
                )


            },
        )
    }
}

@Composable
fun ResourceImage(
    path: String,
    modifier: Modifier = Modifier,
    onState: (ImageLoadState) -> Unit = {},
    contentDescription: String? = null,
) {
    ResourceImage(rememberResourceImageState(path), modifier, onState, contentDescription)
}

fun uriToCoil(uri: String) = URI(uri).toPath().absolutePathString()

suspend fun getResourceUri(path: String): String {
    return withContext(Dispatchers.IO) {
        Res.getUri(path)
    }
}

suspend fun uriExists(uri: String): Boolean {
    return withContext(Dispatchers.IO) {
        URI(uri).toPath().exists()
    }
}
