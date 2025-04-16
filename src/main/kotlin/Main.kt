package natan

import androidx.compose.material.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.natanfudge.fu.hotreload.FunHotReload
import kotlinx.coroutines.runBlocking
import natan.io.github.natanfudge.fu.window.GlfwWebgpuWindow



// -XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=core, use jbr_25
fun main() {
    val window = GlfwWebgpuWindow()

    FunHotReload.observation.observe {
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


