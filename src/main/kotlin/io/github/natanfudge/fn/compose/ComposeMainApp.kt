package io.github.natanfudge.fn.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay


@Composable
fun ComposeMainApp() {
    println("Recompose ")

    var color by remember { mutableStateOf(Color.Red) }
    LaunchedEffect(Unit) {
        delay(500)
        color = Color.Blue
    }
    // We add a little padding so skia won't flag our color as "the background color" and this will conflict
    // with the fact we override the background color to be transparent.
    Box(Modifier.padding(10.dp).border(1.dp, Color.Green).background(color).fillMaxSize()) {
        Column {
            Button(onClick = {color = if(color == Color.Red) Color.Blue else Color.Red}) {
                Text("Value = ${color}", color = Color.White, fontSize = 30.sp)
            }

            Text("New thing f", color = Color.White)

        }

    }
}