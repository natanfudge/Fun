package io.github.natanfudge.fn

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// KEEP IN MIND: If we could improve wgpu error handling, we could figure out why we get index out of bounds exceptions when we restart sometimes


const val HOT_RELOAD_SHADERS = true

fun main() {
//    val list = mutableListOf<@Composable () -> Unit>()
//    list.add {
//
//    }
    application {
        Window(::exitApplication) {
            Text("gg")
//            list.forEach { it() }
        }
    }
}


//val lambdas = mutableListOf<Runnable>()
//fun main() {
//    subcallAdd(1 to 2)
//    while (true) {
//        Thread.sleep(100)
//        subcallRun()
//    }
//}
//
//private fun subcallRun() {
//    lambdas.forEach { it.run() }
//}
//
//private fun subcallAdd(x: Pair<Int, Int>) {
//    val lambda = object : Runnable {
//        override fun run() {
//            println(x.first)
//            println("Halo1")
//        }
//    }
//
//    lambdas.add(lambda)
//}
//TODO: current workaround i'm thinking of is evicting and recreating the lfiecycle lambdas, and having a try/catch for that one frame where it fails.


// To sum up the current commandments of DCEVM:
// 1. Thou shalt not keep changing code next to long-running code: https://github.com/JetBrains/JetBrainsRuntime/issues/534
// 2. Thou shalt not store a lambda in memory without recreating it on reload: https://github.com/JetBrains/JetBrainsRuntime/issues/535
// 3. Thou shalt not store an anonymous class in memory without recreating it on reload: https://github.com/JetBrains/JetBrainsRuntime/issues/536
// 4. Thou shalt not use inheritance: https://youtrack.jetbrains.com/issue/JBR-8575/After-removing-override-calling-the-invokevirtual-causes-NPE