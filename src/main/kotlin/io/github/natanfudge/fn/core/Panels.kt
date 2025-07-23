package io.github.natanfudge.fn.core

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.memory.MemoryCache
import io.github.natanfudge.fn.compose.ComposeOpenGLRenderer
import io.github.natanfudge.fn.compose.utils.clickableWithNoIndication
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.mte.PanelWindowDimensions
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.util.Lifecycle
import io.github.natanfudge.fn.window.WindowDimensions
import io.github.natanfudge.fn.window.WindowParameters

data class ComposeHudPanel(val modifier: BoxScope. () -> Modifier, val content: @Composable BoxScope.() -> Unit, val panels: Panels) : AutoCloseable {
    override fun close() {
        panels.closePanel(this)
    }
}


class Panels {
    private val panelList = mutableStateListOf<ComposeHudPanel>()
    private var worldGui: WorldPanelManager? = null // We only support one panel for now
//    var worldGui: (ComposeWorldPanel)? by mutableStateOf(null)

    /**
     * @param transform Where the panel is placed in the world
     * @param canvasWidth Together with [canvasHeight], specified what is the pixel size of the 2D page that will be drawn into.
     * Large canvases have more space but might scale badly to the panel placed in the world, which has a much lower pixel size.
     * For this reason its best to choose a canvas size that is close to the size you expect it to be in the player's viewport when displayed in the 3D world.
     */
    fun addWorldPanel(transform: Transform, canvasWidth: Int, canvasHeight: Int, content: (@Composable () -> Unit)): WorldPanel {
        if (worldGui == null) {
            this.worldGui = WorldPanelManager(IntSize(canvasWidth, canvasHeight))
        }
        this.worldGui!!.render.localTransform.transform = transform
        this.worldGui!!.setContent(content)
        return this.worldGui!!
    }

    internal fun clearPanels() {
        panelList.clear()
        this.worldGui = null
    }

    fun addPanel(modifier: BoxScope. () -> Modifier = { Modifier }, content: @Composable BoxScope.() -> Unit): ComposeHudPanel {
        val panel = ComposeHudPanel(modifier, content, this)
        panelList.add(panel)
        return panel
    }

    fun closePanel(panel: ComposeHudPanel) {
        panelList.remove(panel)
    }

    var acceptMouseEvents = true

    /**
     * Allows placing "Panels", which block clicks from reaching the game when they are clicked.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun PanelSupport() {
        Box(Modifier.fillMaxSize()) {
            // Allow clicking outside of the UI to lose focus of the UI
            Box(Modifier.fillMaxSize().focusable().clickableWithNoIndication {})

            for (panel in panelList) {
                Box(
                    panel.modifier(this)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    // Exited - accept mouse events
                                    // Did anything but exit - don't accept mouse events
                                    acceptMouseEvents = event.type == PointerEventType.Exit
                                }
                            }
                        }
                ) {
                    panel.content(this)
                }
            }

        }
    }



}


interface WorldPanel {
    var transform: Transform
    var canvasSize: IntSize
}

//TODO: I really want to memoize this, takes a decent amount of time (>100ms) to start this every hot reload
private class WorldPanelManager(initialPanelSize: IntSize) : Fun("WorldPanelManager"), WorldPanel { //TODo: only need autoclose and context
    val render by render(Model(Mesh.UnitSquare, "WorldPanel"))

    fun setContent(content: @Composable () -> Unit) {
        renderer.windowLifecycle.assertValue.setContent {
            content()
        }
    }

    override var canvasSize: IntSize = initialPanelSize
        set(value) {
            field = value
            window.restart()
        }

    val panelLifecycle = Lifecycle.create<Unit, Unit>("PanelLifecycle") {}

    val window = panelLifecycle.bind<WindowDimensions>("Bar") {
        PanelWindowDimensions(canvasSize.width, canvasSize.height)
    }


    val renderer = ComposeOpenGLRenderer(
        WindowParameters(), windowDimensionsLifecycle = window, beforeFrameEvent = context.beforeFrame,
        name = "WorldPanel", show = false, onSetPointerIcon = {}//TODO
        , onError = {
            context.events.guiError.emit(it)
        },
        parentLifecycle = panelLifecycle
    )

    override var transform: Transform by render::transform


    init {
        panelLifecycle.start(Unit)
//        window.start(Unit)
//        renderer.glfw.windowLifecycle.start(Unit)


        renderer.windowLifecycle.assertValue.frameStream.listen {
            val image = FunImage(IntSize(it.width, it.height), it.bytes, null)
            render.setTexture(image)
        }

        events.appClosed.listen {
            panelLifecycle.end()
//            window.end()
//            renderer.glfw.windowLifecycle.end()
        }
    }
}


//TODO: current issue is that renderer is not drawing transparently textures, although it did work before, very weird