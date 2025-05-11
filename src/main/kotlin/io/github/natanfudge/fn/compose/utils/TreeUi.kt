package io.github.natanfudge.fn.compose.utils

import io.github.natanfudge.fn.util.Tree
import io.github.natanfudge.fn.util.TreePath

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.unit.*

@Composable
fun <T, L> LabeledTreeLayout(
    tree: LabeledEdgeTree<T, L>,
    node: @Composable (T, TreePath, hasContent: Boolean) -> Unit,
    edge: @Composable (L?, totalBrotherCount: Int) -> Unit,
    layerSpacing: Dp = Dp.Hairline,
    nodeSpacing: Dp = Dp.Hairline,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    lineColor: Color = Color.Black
) {
    val layers = tree.tree.collectLayers()

    SubcomposeLayout(modifier) { constraints ->
        val layerMarginPx = layerSpacing.roundToPx()
        val nodePaddingPx = nodeSpacing.roundToPx()

        val placeablesByLayer = measureNodes(layers, node)

        // Calculate total tree height
        val height = calculateTreeHeight(placeablesByLayer, layerMarginPx)
        // Calculate total tree width
        val width = calculateTreeWidth(placeablesByLayer, nodePaddingPx)
        val nodePlacements = placeNodes(placeablesByLayer, verticalArrangement, horizontalArrangement, width)

        println("Halo")

        val nodeRects = nodePlacements.associate { it.path to it.placement }
        val edges = tree.collectEdges(depth = Int.MAX_VALUE)
        // Now that everything has been positioned, we can position the lines accordingly.
        val connections = subcompose(ConnectionsSlot) {
            Canvas(Modifier.fillMaxSize()) {
                edges.forEach { edge ->
                    val parentPos: IntRect = nodeRects[edge.parentPath] ?: error("Missing placement for parent")
                    val childPos = nodeRects[edge.childPath]
                    if (childPos == null) {
                        println(
                            "ERROR: Missing placement for child. This shouldn't happen in normal execution. " +
                                    "Child:${edge.childPath}, Rects: $nodeRects"
                        )
                        return@forEach
                    }

                    drawLine(lineColor, parentPos.bottomCenter.toOffset(), childPos.topCenter.toOffset())
                }
            }
        }.map { it.measure(Constraints()) }

        // Now we can position the edges
        val edgePlaceables = edges.mapIndexed { i, edge ->
            edge to (subcompose(i, content = { edge(edge.label, edge.totalBrotherCount) })
                .singleOrNull()?.measure(Constraints())
                ?: error("Expected exactly one composable representing a tree edge"))
        }


        layout(width, height) {
            for (placement in nodePlacements) {
                placement.placeable.place(placement.placement.topLeft)
            }
            // Place edge connection lines
            connections.forEach {
                it.place(0, 0)
            }
            // Place edge labels
            for ((edge, placeable) in edgePlaceables) {
                val parentPos = nodeRects[edge.parentPath]!!
                val childPos = nodeRects[edge.childPath] ?: continue
                val midpoint = parentPos.bottomCenter.midpointTo(childPos.topCenter)
                val position = midpoint - IntOffset(placeable.width / 2, placeable.height / 2)
                placeable.place(position)
            }
        }
    }
}

private fun IntOffset.midpointTo(other: IntOffset) = IntOffset((this.x + other.x) / 2, (this.y + other.y) / 2)

private fun <T> SubcomposeMeasureScope.measureNodes(
    layers: List<List<CollectedEdge<T>>>,
    node: @Composable ((T, TreePath, hasContent: Boolean) -> Unit),
): List<List<NodeMeasurement<T>>> {
    val placeablesByLayer = layers.map { layer ->
        layer.map { (nodeValue, path, canExpand) ->
            val placeable = subcompose(path, { node(nodeValue, path, canExpand) })
                .singleOrNull()?.measure(Constraints())
                ?: error("Expected only one composable representing a tree node")
            NodeMeasurement(nodeValue, placeable, path)
        }
    }
    return placeablesByLayer
}

private fun <T> calculateTreeWidth(
    placeablesByLayer: List<List<NodeMeasurement<T>>>,
    nodePaddingPx: Int,
): Int = placeablesByLayer.maxOf { row ->
    row.sumOf { it.placeable.width } + (nodePaddingPx * (row.size - 1))
}

private fun <T> calculateTreeHeight(
    placeablesByLayer: List<List<NodeMeasurement<T>>>,
    layerMarginPx: Int,
): Int = placeablesByLayer.sumOf { row -> row.maxOf { item -> item.placeable.height } } +
        (layerMarginPx * (placeablesByLayer.size - 1))

data class NodeMeasurement<T>(
    val value: T,
    val placeable: Placeable,
    val path: TreePath,
)

data class NodePlacement<T>(
    val value: T,
    val placeable: Placeable,
    val placement: IntRect,
    /**
     * Used as a unique identifier for nodes
     */
    val path: TreePath,
)

private fun <T> SubcomposeMeasureScope.placeNodes(
    placeablesByLayer: List<List<NodeMeasurement<T>>>,
    verticalArrangement: Arrangement.Vertical,
    horizontalArrangement: Arrangement.Horizontal,
    width: Int,
): List<NodePlacement<T>> {
    // Place items using passed arrangements
    val yOffsets = IntArray(placeablesByLayer.size)
    with(verticalArrangement) {
        arrange(
            totalSize = Int.MAX_VALUE,
            sizes = placeablesByLayer.map { row -> row.maxOf { it.placeable.height } }.toIntArray(),
            outPositions = yOffsets
        )
    }

    val nodePlacements = placeablesByLayer.flatMapIndexed { rowNum, row ->
        val xOffsets = IntArray(row.size)
        with(horizontalArrangement) {
            arrange(
                width,
                row.map { it.placeable.width }.toIntArray(),
                layoutDirection, xOffsets
            )
        }
        row.mapIndexed { colNum, item ->
            val x = xOffsets[colNum]
            val y = yOffsets[rowNum]

            NodePlacement(
                value = item.value,
                placeable = item.placeable,
                placement = IntRect(x, y, x + item.placeable.width, y + item.placeable.height),
                path = item.path
            )
        }
    }
    return nodePlacements
}

private object ConnectionsSlot
