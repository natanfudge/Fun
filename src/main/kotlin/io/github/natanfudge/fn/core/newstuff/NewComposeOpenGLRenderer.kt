@file:OptIn(InternalComposeUiApi::class)

package io.github.natanfudge.fn.core.newstuff

import io.github.natanfudge.fn.compose.GlfwComposeScene
import io.github.natanfudge.fn.compose.glDebugGroup


import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.*
import io.github.natanfudge.fn.compose.ComposeFrameEvent
import io.github.natanfudge.fn.compose.FixedSizeComposeWindow
import io.github.natanfudge.fn.window.*
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.glfwGetWindowContentScale
import org.lwjgl.glfw.GLFW.glfwSwapBuffers
import org.lwjgl.opengl.GL


internal class NewComposeOpenGLRenderer(
     params: WindowConfig,
    val name: String,
    onSetPointerIcon: (PointerIcon) -> Unit,
    onError: (Throwable) -> Unit,
    onFrame: (ComposeFrameEvent) -> Unit,
    show: Boolean = false,
): NewFun("ComposeOpenGLRenderer-$name") {

    private val offscreenWindow = NewGlfwWindowHolder(withOpenGL = false, showWindow = show, params)

    val scene by cached(offscreenWindow.window) {
        val handle = offscreenWindow.window.handle
        GLFW.glfwMakeContextCurrent(handle)
        val size = offscreenWindow.size
        val capabilities = GL.createCapabilities()

        glDebugGroup(1, groupName = { "$name Compose Init" }) {
            GlfwComposeScene(
                size, handle,
                density = Density(glfwGetWindowContentScale(handle)),
                onSetPointerIcon = onSetPointerIcon,
                label = name,
                onError = {
                    //TODo
//                    windowLifecycle.restart()
                    onError(it)
                }, capabilities = capabilities, getWindowSize = {
                    offscreenWindow.size
                }, onInvalidate = {
                    // Invalidate Compose frame on change
                    it.invalid = true
                }
            )
        }
    }

    var canvas by cached(offscreenWindow.window) {
        FixedSizeComposeWindow(offscreenWindow.size, scene)
    }

    fun resize(size: IntSize) {
        scene.invalid = true
        canvas = FixedSizeComposeWindow(size, scene)
    }

    init {

        events.beforeFrame.listenUnscoped {

            scene.dispatcher.poll()

            if (scene.invalid) {
                GLFW.glfwMakeContextCurrent(scene.handle)
                GL.setCapabilities(scene.capabilities)

                canvas.renderSkia()
                onFrame(canvas.blitFrame())

                glfwSwapBuffers(scene.handle)
                scene.invalid = false
            }
        }
    }
}


private fun glfwGetWindowContentScale(window: Long): Float {
    val array = FloatArray(1)
    glfwGetWindowContentScale(window, array, FloatArray(1))
    return array[0]
}
