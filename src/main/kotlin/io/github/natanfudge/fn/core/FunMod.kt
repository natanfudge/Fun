package io.github.natanfudge.fn.core

import androidx.compose.runtime.Composable
import kotlin.time.Duration

@Deprecated("No need for this anymore, use FunContext.events and FunContext.addGuiPanel")
interface FunMod {
    @Composable
    @Deprecated("")
    fun ComposePanelPlacer.gui() {

    }

    @Deprecated("")
    fun handleInput(input: InputEvent){}
    @Deprecated("")
    fun frame(deltaMs: Double) {

    }
    @Deprecated("")
    fun prePhysics(delta: Duration) {

    }
    @Deprecated("")
    fun postPhysics(delta: Duration) {

    }
    @Deprecated("")
    fun onGUIError(error: Throwable) {

    }
}