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
import io.github.natanfudge.fn.compose.ComposeOpenGLRenderer
import io.github.natanfudge.fn.compose.utils.Holder
import io.github.natanfudge.fn.compose.utils.clickableWithNoIndication
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.render.Mesh
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.fn.window.WindowConfig
import io.github.natanfudge.wgpu4k.matrix.Mat4f

data class ComposeHudPanel(val modifier: BoxScope. () -> Modifier, val content: @Composable BoxScope.() -> Unit, val panels: FunPanels) : AutoCloseable {
    override fun close() {
        panels.closePanel(this)
    }
}


class FunPanels : Fun("FunPanels") {
    companion object {
        val service = serviceKey<FunPanels>()
    }
    init {
        exposeAsService(service)
    }
    private val panelList by memo<MutableList<ComposeHudPanel>> { mutableStateListOf() }

    private var worldGui: WorldPanelManager? = null // We only support one panel for now
//    var worldGui: (ComposeWorldPanel)? by mutableStateOf(null)

    /**
     * @param transform Where the panel is placed in the world
     * @param canvasWidth Together with [canvasHeight], specified what is the pixel size of the 2D page that will be drawn into.
     * Large canvases have more space but might scale badly to the panel placed in the world, which has a much lower pixel size.
     * For this reason its best to choose a canvas size that is close to the size you expect it to be in the player's viewport when displayed in the 3D world.
     */
    fun addUnscopedWorldPanel(transform: Transform, size: IntSize, content: (@Composable () -> Unit)): WorldPanel {
        if (worldGui == null) {
            this.worldGui = WorldPanelManager(size)
        } else {
            this.worldGui?.canvasSize = size
        }
        this.worldGui!!.render.localTransform.set(transform)
        this.worldGui!!.setContent(content)
        return WorldPanel(worldGui!!)
    }

    @Deprecated("Use scoped addPanel to avoid keeping around a GUI of a dead Fun", replaceWith = ReplaceWith("addPanel(modifier, content)"))
    fun addUnscopedPanel(modifier: BoxScope. () -> Modifier = { Modifier }, content: @Composable BoxScope.() -> Unit): ComposeHudPanel {
        val panel = ComposeHudPanel(modifier, content, this)
        panelList.add(panel)
        return panel
    }

    fun closePanel(panel: ComposeHudPanel) {
        panelList.remove(panel)
    }

    internal var userInGui by memo { Holder(false) }

    /**
     * Allows placing "Panels", which block clicks from reaching the game when they are clicked.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    internal fun PanelSupport() {
        PanelSupportLongLiving(panelList, userInGui)
    }

}

/**
 * This Composable can outlive the [FunPanels] instance so we need to make sure this doesn't reference stale values
 */
@Composable
private fun PanelSupportLongLiving(
    /**
     * Both of these are eternal objects living in memo {}
     */
    panelList: MutableList<ComposeHudPanel>,
    userInGUI: Holder<Boolean>,
) {

    Box(Modifier.fillMaxSize()) {
        // Allow clicking outside the UI to lose focus of the UI
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
                                userInGUI.value = event.type != PointerEventType.Exit
                            }
                        }
                    }
            ) {
                panel.content(this)
            }
        }

    }
}


class WorldPanel internal constructor(private val panelManager: WorldPanelManager) : AutoCloseable {
    var canvasSize: IntSize = panelManager.initialPanelSize
        set(value) {
            field = value
            panelManager._compose.resize(value)
        }

    override fun close() {
        // Get rid of the content, simplest way to do this for now
        panelManager.resetUi()
    }
}


internal class WorldPanelManager(val initialPanelSize: IntSize) : Fun("WorldPanelManager") {
    val render by render(Model(Mesh.UnitSquare, "WorldPanel"))

    private var currentContent: @Composable () -> Unit by memo { {} }


    val _compose = ComposeOpenGLRenderer(
        WindowConfig(size = initialPanelSize), show = false,
        onSetPointerIcon = {}, // Not yet
        name = "WorldPanels",
        onFrame = { (bytes, size) ->
            val image = FunImage(size, bytes, null)
            render.setTexture(image)
        },
        onCreateScene = { scene ->
            scene.setContent {
                currentContent()
            }
        },
        parent = this
    )

    /**
     * Makes the GUI be blank
     */
    fun resetUi() {
        this.currentContent = {}
        if (_compose.scene.valid) {
            // Don't want to do this when closing the scene anyway
            _compose.scene.setContent { }
        }
    }

    fun setContent(content: @Composable () -> Unit) {
        currentContent = content
        _compose.scene.setContent(content)
    }

    init {
        _compose.resize(initialPanelSize)
    }


    var canvasSize: IntSize = initialPanelSize
        set(value) {
            field = value
            _compose.resize(value)
        }
}


