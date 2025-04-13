package natan

import io.github.natanfudge.fu.hotreload.FunHotReload

var x = 0
fun process() {
    while (true) {
        Thread.sleep(1000)
        println(x++)
    }
}

fun main() {
    FunHotReload.detectHotswap()
    FunHotReload.observation.observe {
        println("Reloadeffggfffggrwd")
        x = 90
    }
    process()
}