package io.github.natanfudge.fn

import io.github.natanfudge.fn.hotreload.FunHotReload
import io.github.natanfudge.fn.window.GlfwComposeWindow

interface Restartable<T> {
    fun restart(params: T? = null)
}


// -XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=core, use jbr_25
//fun main() {
//    val window = GlfwComposeWindow()
//
//    FunHotReload.observation.listen {
//        println("Reloading")
//        window.submitTask {
//            // Very important to run this on the main thread
//            window.restart()
//        }
//    }
//    window.show()
//}




