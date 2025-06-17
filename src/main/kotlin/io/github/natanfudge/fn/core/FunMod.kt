package io.github.natanfudge.fn.core

import androidx.compose.runtime.Composable

interface FunMod {
    @Composable
    fun ComposePanelPlacer.gui() {

    }

    fun handleInput(input: InputEvent){}

    fun frame(delta: Float) {

    }

    fun physics(delta: kotlin.time.Duration) {

    }

    fun cleanup() {

    }
}