package io.github.natanfudge.fn.core

import androidx.compose.runtime.Composable
import kotlin.time.Duration

interface FunMod {
    @Composable
    fun ComposePanelPlacer.gui() {

    }

    fun handleInput(input: InputEvent){}

    fun frame(delta: Float) {

    }

    fun prePhysics(delta: Duration) {

    }

    fun postPhysics(delta: Duration) {

    }

    fun cleanup() {

    }
}