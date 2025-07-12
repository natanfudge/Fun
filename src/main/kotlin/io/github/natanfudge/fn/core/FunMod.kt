package io.github.natanfudge.fn.core

import androidx.compose.runtime.Composable
import kotlin.time.Duration

@Deprecated("No need for this anymore, use FunContext.events and FunContext.addGuiPanel")
interface FunMod {

    @Deprecated("")
    fun prePhysics(delta: Duration) {

    }

}