package io.github.natanfudge.fn

import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.window.GlfwWebgpuWindow


// -XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=core, use jbr_25
fun main() {
    val window = GlfwWebgpuWindow()
    println("Start 4")

    FunHotReload.observation.listen {
        println("Reloading")
        window.submitTask {
            // Very important to run this on the main thread
            window.restart()
        }
    }
    window.show()
//    application {
//        Window(::exitApplication) {
//            Text("ALO")
//        }
//    }
}




