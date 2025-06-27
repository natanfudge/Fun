package io.github.natanfudge.fn.mte

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.natanfudge.fn.base.FunPanel
import io.github.natanfudge.fn.core.ComposePanelPlacer
import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import natan.`fun`.generated.resources.Res
import java.net.URI
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.toPath

class Inventory(val game: MineTheEarth) : Fun("Inventory", game.context) {
    val items = funList<Item>("items", mutableStateListOf())

    val maxSlots = 15

    val itemsPerRow = 10
    private val slotWidth = 60.dp

    @Composable
    fun ComposePanelPlacer.InventoryGUI() {
        FunPanel(Modifier.align(Alignment.BottomCenter)) {
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
                            for ((col, item) in row.withIndex()) {
                                if (col > 0) {
                                    VerticalDivider(thickness = 2.dp)
                                }
                                DisplayItem(item)
                            }
                            repeat(itemsPerRow - row.size) {
                                VerticalDivider(thickness = 2.dp)
                                Box(modifier = Modifier.size(totalSlotWidth))
                            }
                        }

                    }
                    if (items.isEmpty()) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            repeat(itemsPerRow) {
                                if (it > 0) VerticalDivider(thickness = 2.dp)
                                Box(modifier = Modifier.size(totalSlotWidth))
                            }
                        }
                    }
                }
            }
        }
    }

    //TODO: one last thing - stack merging

    @Composable
    private fun DisplayItem(item: Item) {
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
