package io.github.natanfudge.fn.mte

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
        Box {
            val image by produceState<Pair<Boolean, Any?>?>(null) {
                val uri = getResourceUri("files/icons/items/${item.type.name.lowercase()}.png")
                if (uriExists(uri)) {
                    value = true to uriToCoil(uri)
                } else {
                    value = false to uriToCoil(uri)
                }
//                value = uriExists(uri)
            }

//            var loadState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
            if (image == null) {
                Box(Modifier.size(slotWidth))
            } else if (image!!.first) {
                AsyncImage(
                    model = image!!.second,
                    contentDescription = item.type.toString(),
                    Modifier.size(slotWidth),
//                    onState = { loadState = it }
                )
            } else {
                Text(item.type.toString(), modifier = Modifier.size(slotWidth).padding(5.dp))
            }
            if (image != null) {
                Text(item.count.toString(), Modifier.align(Alignment.BottomEnd).padding(5.dp))
            }
        }

    }
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
