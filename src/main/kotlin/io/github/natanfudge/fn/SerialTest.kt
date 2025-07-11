package io.github.natanfudge.fn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class Foo(val x: Int) {
    var y by mutableStateOf(1)
    val z = 2
}

fun main() {
    val f = Foo(1)
    f.y = 5
    val toHell = Json.encodeToString(f)
    val andBack = Json.decodeFromString<Foo>(toHell)
    println(andBack.y)
}