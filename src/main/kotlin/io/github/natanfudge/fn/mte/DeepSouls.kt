package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.base.*
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.core.funValue
import io.github.natanfudge.fn.core.startTheFun
import io.github.natanfudge.fn.mte.gui.MainMenu
import io.github.natanfudge.fn.mte.gui.addDsHudPanel
import io.github.natanfudge.fn.physics.RootTransformable
import io.github.natanfudge.fn.render.CameraMode
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.reflect.KClass
import io.github.natanfudge.fn.render.FunRenderState
import io.ygdrasil.wgpu.WGPUQueryType_Occlusion

class DeepSouls() : Fun("DeepSouls") {
    val inMainMenuState = funValue("inMainMenu", {true}){}
    var inMainMenu by inMainMenuState

    init {
        if (!inMainMenu) {
            DeepSoulsGame()
        }

        addDsHudPanel {
            MainMenu(this@DeepSouls)
        }
    }
}


class DeepSoulsGame : Fun("Game") {
    companion object {
        val SurfaceZ = 100
    }

    val balance by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Balance.create()
    }

    val animation = FunAnimation()
    val background = Background()
    val physics = FunPhysics()
    val player = Player(this)
    val devil = Devil()


    val visualEditor = VisualEditor(enabled = false)

    val world = World(this)


    var cameraDistance by funValue(15f)

    private fun repositionCamera(playerPos: Vec3f) {
        if (creativeMovement.mode == CameraMode.Off) {
            camera.setLookAt(playerPos + Vec3f(0f, -cameraDistance, 0f), forward = Vec3f(0f, 1f, 0f))
            // This one is mostly for zooming in
            camera.focus(playerPos, distance = cameraDistance)
        }
    }




    val creativeMovement = CreativeMovement(input)

    fun initialize() {
        player.physics.position = Vec3f(0f, 0.5f, 110f)
//        player.physics.position = Vec3f(0f, 0.5f, 105f)
        player.animation.play("jump", loop = false)

        world.initialize()
    }

    init {
        physics.system.earthGravityAcceleration = 20f

        player.physics.positionState.afterChange {
            repositionCamera(it)
        }


        input.registerHotkey("Zoom Out", ScrollDirection.Down, ctrl = true) {
            cameraDistance += 1f
            repositionCamera(player.physics.position)
        }

        input.registerHotkey("Zoom In", ScrollDirection.Up, ctrl = true) {
            cameraDistance -= 1f
            repositionCamera(player.physics.position)
        }

        FunDebugPanel()
        ErrorNotifications()
        HoverHighlight(redirectHover = {
            if (visualEditor.enabled) it
            else if (it is Block) player.targetBlock(it)
            else null
        }, hoverRenderPredicate = {
            if (visualEditor.enabled) true
            // Don't highlight the break overlay
            else !it.id.endsWith(Block.BreakRenderId)
        })
    }
}

class FunTestContext(private val context: FunContext): FunContext by context {
    /**
     * Asserts that [count] instances of [T] exist in the game.
     *
     * Can use [count] = 0 to assert that no instances exist
     *
     * @return [count] instances of [T] that exist in the game.
     */
    inline fun <reified T: Fun> assertExists(count: Int = 1): List<T> {
        TODO()
    }

    /**
     * Asserts that [count] instances of [T] are visible on the screen.
     * This means that they have a child [FunRenderState], that is being rendered to the screen with at least one pixel of it visible from the perspective
     * and not hidden by other objects.
     *
     * Can use [count] = 0 to assert that no instances are visible.
     */
    inline fun <reified T: Fun> assertVisible(count: Int = 1) {

    }

    /**
     * Asserts that all items of this list are visible on the screen.
     * This means that they have a child [FunRenderState], that is being rendered to the screen with at least one pixel of it visible from the perspective
     * and not hidden by other objects.
     */
    fun  <T: Fun> List<T>.assertVisible() {

    }

    fun simulateInput(input: InputEvent) {
    }

}
//TODO: 1. Implement this:
fun funTest(test: suspend context(FunTestContext) () -> Unit) {

}
// Which starts a game, and then closes when the callback finishes.

// 2. Uncomment Main.kt and adjust to new APIs, and rename it to "FunTestApp.kt"
// 3. Make it so our test case simply launches the test app, delay(5.seconds),  and closes.
// 4. Understand what exactly is being test in FunTestApp, and add more assertion definitions to FunTestContext. Need to think how we can test visual things better. Maybe AI.
// 5. Implement assertions & simulated controls
// 6. Completely test the test app
// 7. Add some more test cases from DeepSouls
// 8. Implement GPU memory freeing
// 9. Test memory freeing by having a small max buffer and deleting & recreating stuff, and then checking if stuff looks correct
// 10. Implement GPU memory resizing (For some things)
// 11. Test resizing by starting with a small buffer and adding a lot of things, and then checking if stuff looks correct.



fun main() {
    startTheFun {
        DeepSouls()
    }
}