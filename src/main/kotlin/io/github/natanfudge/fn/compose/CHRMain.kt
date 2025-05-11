package io.github.natanfudge.fn.compose

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    val list = mutableListOf<@Composable () -> Unit>()
    list.add {

    }
    application {
        Window(::exitApplication) {
            Text("Henlo")
            list.forEach { it() }
        }
    }
}



//fun main() {
//    val list = mutableListOf<@Composable () -> Unit>()
//    list.add {
//
//    }
//    application {
//        Window(::exitApplication) {
//            Text("Henlo")
//            list.forEach { it() }
//        }
//    }
//}
