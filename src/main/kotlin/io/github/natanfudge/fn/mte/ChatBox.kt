package io.github.natanfudge.fn.mte

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.mte.gui.addDsWorldPanel
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.util.PIf
import io.github.natanfudge.fn.window.WindowDimensions
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f

data class PanelWindowDimensions(override val width: Int, override val height: Int) : WindowDimensions
class ChatBox(game: DeepSoulsGame) : Fun("ChatBox") {
    var counter by mutableStateOf(0)

    val panel = addDsWorldPanel(
        Transform(
            translation = Vec3f(0f, 0f, 102f),
            rotation = Quatf.xRotation(PIf / -2),
            scale = Vec3f(6f, 1f, 3f)
        ),
        800, 200
    ) {
        Surface {
            Text("Halo")
//            val modelProducer = remember { CartesianChartModelProducer() }
//            LaunchedEffect(Unit) {
//                modelProducer.runTransaction {
//                    columnSeries { series(5, 6, 5, 2, 11, 8, 5, 2, 15, 11, 8, 13, 12, 10, 2, 7) }
//                }
//            }
//            CartesianChartHost(
//                rememberCartesianChart(
//                    rememberColumnCartesianLayer(),
//                    startAxis = VerticalAxis.rememberStart(),
//                    bottomAxis = HorizontalAxis.rememberBottom(),
//                ),
//                modelProducer,
//            )
        }

    }

    init {
        game.input.registerHotkey("Test", Key.T) {
            counter++
        }
    }
}
