package io.github.natanfudge.fn.test.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.FunTime
import io.github.natanfudge.fn.core.FunWindow
import io.github.natanfudge.fn.core.FunWorldRender
import io.github.natanfudge.fn.core.Panels
import io.github.natanfudge.fn.network.FunId
import io.github.natanfudge.fn.network.FunStateContext
import io.github.natanfudge.fn.physics.Renderable
import io.github.natanfudge.fn.render.BoundModel
import io.github.natanfudge.fn.render.DefaultCamera
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.RenderInstance
import io.github.natanfudge.fn.render.Tint
import io.github.natanfudge.wgpu4k.matrix.Mat4f
import kotlin.time.Duration

class PhysicsSimulationTime() : FunTime {
    override var speed: Float = 0f
    override fun advance(time: Duration) {

    }

    override fun stop() {
    }

    override fun resume() {
    }

    override fun _poll() {

    }
}

class PhysicsSimulationFunContext() : FunContext, FunStateContext by FunStateContext.isolatedClient() {
    override val world: FunWorldRender = PhysicsSimulationFunWorld
    override val window: FunWindow = PhysicsSimulationWindow
    override val time: FunTime = PhysicsSimulationTime()
    override val panels: Panels = Panels()

    override fun setCursorLocked(locked: Boolean) {
    }

    override fun setGUIFocused(focused: Boolean) {
    }

    override var camera: DefaultCamera = DefaultCamera()

}

object PhysicsSimulationWindow: FunWindow {
    override val width: Int = 0
    override val height: Int = 0
    override val aspectRatio: Float = 0f
    override var fovYRadians: Float = 0f
}


object PhysicsSimulationFunWorld : FunWorldRender {
    override var cursorPosition: Offset? = null

    override val hoveredObject: Any? = null

    override fun getOrBindModel(model: Model) = PhysicsSimulationModel

}

object PhysicsSimulationModel : BoundModel {
    override fun getOrSpawn(
        id: FunId,
        value: Renderable,
        tint: Tint,
    ) = PhysicsSimulationInstance

}

object PhysicsSimulationInstance : RenderInstance {
    override fun setTransform(transform: Mat4f) {
    }

    override fun setTintColor(color: Color) {
    }

    override fun setTintStrength(strength: Float) {
    }

    override fun despawn() {

    }
}