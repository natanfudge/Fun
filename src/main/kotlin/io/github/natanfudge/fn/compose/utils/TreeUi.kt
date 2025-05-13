package io.github.natanfudge.fn.compose.utils

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.unit.*
import io.github.natanfudge.fn.util.Tree
import io.github.natanfudge.fn.util.TreePath
import io.github.natanfudge.fn.util.collectEdges

private fun <T> List<List<CollectedVertex<T>>>.distinct(): List<List<CollectedVertex<T>>> {
    val seen = mutableSetOf<T>()

    return map { layer ->
        layer.filter {
            seen.add(it.value)
        }
    }.filter { it.isNotEmpty() }
}

//TODO: window resize has regressed - new frame does not appear in real time anymore

@Composable
fun <T> DagLayout(
    tree: Tree<T>,
    node: @Composable (T, TreePath, hasContent: Boolean) -> Unit,
    layerSpacing: Dp = Dp.Hairline,
    nodeSpacing: Dp = Dp.Hairline,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    lineColor: Color = Color.Black,
) {
    val layers = tree.collectLayers().distinct()
    val layerByNode = layers.mapIndexed { layer, items ->
        items.map { it.value to layer }
    }.flatten().toMap()


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

        // For a DAG, we want to identify each element by its own value, not by its path.
        val nodeRects: Map<T, IntRect> = nodePlacements.associate { it.value to it.placement }
        val edges = tree.collectEdges()
        // Now that everything has been positioned, we can position the lines accordingly.
        val connections = subcompose(ConnectionsSlot) {
            Canvas(Modifier.fillMaxSize()) {
                edges.forEach { edge ->
                    val parentLayer = layerByNode.getValue(edge.parent)
                    val childLayer = layerByNode.getValue(edge.child)
                    val parentPos: IntRect = nodeRects[edge.parent] ?: error("Missing placement for parent")
                    val childPos = nodeRects[edge.child]
                    if (childPos == null) {
                        println(
                            "ERROR: Missing placement for child. This shouldn't happen in normal execution. " +
                                    "Child:${edge.child}, Rects: $nodeRects"
                        )
                        return@forEach
                    }

                    when {
                        parentLayer < childLayer -> {
                            // Parent is above the child
                            drawArrow(parentPos.bottomCenter, childPos.topCenter, lineColor)
                        }
                        parentLayer > childLayer -> {
                            // Parent is below the child
                            drawArrow(parentPos.topCenter, childPos.bottomCenter, lineColor)
                        }
                        else -> {
                            drawCurvedArrow(parentPos, childPos, lineColor)
                        }
                    }


                }
            }
        }.map { it.measure(Constraints()) }

        // LOWPRIO: we can support edges here in the future
        // Now we can position the edges
//        val edgePlaceables = edges.mapIndexed { i, edge ->
//            edge to (subcompose(i, content = {
//                edge(edge.label, edge.totalBrotherCount)
//
//            })
//                .singleOrNull()?.measure(Constraints())
//                ?: error("Expected exactly one composable representing a tree edge"))
//        }


        layout(width, height) {
            for (placement in nodePlacements) {
                placement.placeable.place(placement.placement.topLeft)
            }
            // Place edge connection lines
            connections.forEach {
                it.place(0, 0)
            }
//            // Place edge labels
//            for ((edge, placeable) in edgePlaceables) {
//                val parentPos = nodeRects[edge.parentPath]!!
//                val childPos = nodeRects[edge.childPath] ?: continue
//                val midpoint = parentPos.bottomCenter.midpointTo(childPos.topCenter)
//                val position = midpoint - IntOffset(placeable.width / 2, placeable.height / 2)
//                placeable.place(position)
//            }
        }
    }
}

private fun DrawScope.drawArrow(start: IntOffset, end: IntOffset, color: Color = Color.Black, arrowHeadSize: Float = 10f) {
    // Draw the line (shaft of the arrow)
    drawLine(
        color = color,
        start = start.toOffset(),
        end = end.toOffset(),
        strokeWidth = Stroke.HairlineWidth
    )
    
    // Calculate the angle of the line
    val angle = kotlin.math.atan2(
        (end.y - start.y).toFloat(),
        (end.x - start.x).toFloat()
    ) * (180f / kotlin.math.PI.toFloat())
    
    // Draw the arrowhead
    val endOffset = end.toOffset()
//    save() {
        translate(endOffset.x, endOffset.y) {
            rotate(angle) {
                // Draw a triangle for the arrowhead
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(-arrowHeadSize, -arrowHeadSize / 2)
                    lineTo(-arrowHeadSize, arrowHeadSize / 2)
                    close()
                }
                drawPath(path, color)
            }
        }
//    }

}

private fun DrawScope.drawCurvedArrow(
    start: IntRect,
    end: IntRect,
    lineColor: Color,
    arrowHeadSize: Float = 10f
) {
    // Draw an arc when parent and child are on same layer
    val startX = start.centerRight.x.toFloat()
    val startY = start.centerRight.y.toFloat()
    val endX = end.centerLeft.x.toFloat()
    val endY = end.centerLeft.y.toFloat()

    // Calculate control point for the arc
    val controlX = (startX + endX) / 2
    val controlY = (startY + endY) / 2 + start.height * 2 // Curve upward

    // Draw curved path
    drawPath(
        path = Path().apply {
            moveTo(startX, startY)
            quadraticTo(controlX, controlY, endX, endY)
        },
        color = lineColor,
        style = Stroke(Stroke.HairlineWidth)
    )
    
    // Calculate the tangent direction at the end point
    // For a quadratic Bezier curve, the tangent at endpoint t=1 is in the direction (end - control)
    val tangentX = endX - controlX
    val tangentY = endY - controlY
    
    // Calculate the angle of the tangent
    val angle = kotlin.math.atan2(tangentY, tangentX) * (180f / kotlin.math.PI.toFloat())
    
    // Draw the arrowhead at the end point
    translate(endX, endY) {
        rotate(angle) {
            // Draw a triangle for the arrowhead
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(-arrowHeadSize, -arrowHeadSize / 2)
                lineTo(-arrowHeadSize, arrowHeadSize / 2)
                close()
            }
            drawPath(path, color = lineColor)
        }
    }
}

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
    lineColor: Color = Color.Black,
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
        val edges = tree.collectEdgePaths(depth = Int.MAX_VALUE)
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

                    drawArrow(parentPos.bottomCenter, childPos.topCenter, lineColor)
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
    layers: List<List<CollectedVertex<T>>>,
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
