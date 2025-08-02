@file:OptIn(InternalComposeUiApi::class)

package io.github.natanfudge.fn.core.newstuff


import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.compose.ComposeFrameEvent
import io.github.natanfudge.fn.compose.FixedSizeComposeWindow
import io.github.natanfudge.fn.compose.GlfwComposeScene
import io.github.natanfudge.fn.compose.glDebugGroup
import io.github.natanfudge.fn.window.WindowConfig
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwGetWindowContentScale
import org.lwjgl.glfw.GLFW.glfwSwapBuffers
import org.lwjgl.opengl.GL


internal class NewComposeOpenGLRenderer(
    params: WindowConfig,
    val name: String,
    val onSetPointerIcon: (PointerIcon) -> Unit,
    onFrame: (ComposeFrameEvent) -> Unit,
    val onCreateScene: (scene: GlfwComposeScene) -> Unit,
    show: Boolean = false,
) : NewFun("ComposeOpenGLRenderer-$name") {
    private val offscreenWindow = NewGlfwWindowHolder(
        withOpenGL = true, showWindow = show, params, name = name,
        onEvent = {} // Ignore events from the offscreen window
    )

    var scene: GlfwComposeScene by cached(offscreenWindow.window) {
        createComposeScene()
    }

    private fun createComposeScene(): GlfwComposeScene {
        val handle = offscreenWindow.window.handle
        GLFW.glfwMakeContextCurrent(handle)
        val size = offscreenWindow.size
        val capabilities = GL.createCapabilities()

        val scene = glDebugGroup(1, groupName = { "$name Compose Init" }) {
            GlfwComposeScene(
                size, handle,
                density = Density(glfwGetWindowContentScale(handle)),
                onSetPointerIcon = onSetPointerIcon,
                label = name,
                onError = {
                    events.guiError(it)

                    // Refresh compose so it won't die from one exception
                    scene = createComposeScene()
                    canvas = FixedSizeComposeWindow(offscreenWindow.size, scene)
                }, capabilities = capabilities, getWindowSize = {
                    offscreenWindow.size
                }, onInvalidate = {
                    // Invalidate Compose frame on change
                    it.frameInvalid = true
                }
            )
        }
        onCreateScene(scene)
        return scene
    }

    var canvas by cached(offscreenWindow.window) {
        FixedSizeComposeWindow(offscreenWindow.size, scene)
    }

    fun resize(size: IntSize) {
        scene.frameInvalid = true
        canvas = FixedSizeComposeWindow(size, scene)
    }

    init {

        events.beforeFrame.listen {

            check(!closed)
            check(scene.valid)
            scene.dispatcher.poll()

            if (scene.frameInvalid) {
                GLFW.glfwMakeContextCurrent(scene.handle)
                GL.setCapabilities(scene.capabilities)

                canvas.renderSkia()
                onFrame(canvas.blitFrame())
                println("After onFrame")

                glfwSwapBuffers(scene.handle)
                println("After Compose swapbuffers")
                scene.frameInvalid = false
            }
        }
    }
}


private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}
