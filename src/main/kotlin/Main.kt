package natan

import io.github.natanfudge.fu.hotreload.FunHotReload
import natan.io.github.natanfudge.fu.window.GlfwWebgpuWindow



fun main() {
    FunHotReload.detectHotswap()
    var window = GlfwWebgpuWindow()
    window.show()
    FunHotReload.observation.observe {
        window.close()
        window = GlfwWebgpuWindow()
        window.show()
        println("Reopening window")
    }
}

//TODO: next step:
//TODO: next step: figure out how to save opengl output to bitmap and then we can do whatever we want with it.
// For performance we'll do some vulkan to vulkan stuff.