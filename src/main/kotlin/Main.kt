package natan

import io.github.natanfudge.fu.hotreload.FunHotReload
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import natan.io.github.natanfudge.fu.window.GlfwWebgpuWindow



// -XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=core, use jbr_25
fun main() {
//    FunHotReload.detectHotswap()
    var window = GlfwWebgpuWindow()
    window.show()
    FunHotReload.observation.observe {
        window.close()
        window = GlfwWebgpuWindow()
        window.show()

        println("wgwg")
    }

    Thread.sleep(1000000) // Sleep for 1 second
}


//