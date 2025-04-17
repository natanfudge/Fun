package io.github.natanfudge.fu

import io.github.natanfudge.fu.hotreload.FunHotReload
import io.github.natanfudge.fu.window.GlfwWebgpuWindow


// -XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=core, use jbr_25
fun main() {
    val window = GlfwWebgpuWindow()

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




