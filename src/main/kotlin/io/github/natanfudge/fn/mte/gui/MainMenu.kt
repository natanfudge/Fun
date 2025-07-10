package io.github.natanfudge.fn.mte.gui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.natanfudge.fn.core.FunContext
import io.github.natanfudge.fn.mte.MineTheEarth
import natan.`fun`.generated.resources.MainMenuBG
import natan.`fun`.generated.resources.Res
import natan.`fun`.generated.resources.TitleText
import org.jetbrains.compose.resources.painterResource

@Composable
fun MainMenu(context: FunContext) {
    var inMainMenu by remember { mutableStateOf(true) }
    println("New main")
    if (!inMainMenu) return
    Box(Modifier.fillMaxSize()) {
        MainMenuBackground()
        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            TitleText()
//            Box(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MainMenuButton("Dig Alone", border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                    inMainMenu = false
                    MineTheEarth(context)
                }
                MainMenuButton("Dig With Friends", enabled = false) {}
                MainMenuButton("Settings", fontColor = Color.White.copy(alpha = 0.6f)) {}
                MainMenuButton("Credits", fontColor = Color(120, 120, 200, 200)) {}
            }
//            Box(Modifier.weight(1f))
        }

    }
}

@Composable
private fun MainMenuButton(
    text: String, modifier: Modifier = Modifier, border: BorderStroke? = null,
    enabled: Boolean = true, fontColor: Color = Color.Unspecified, onClick: () -> Unit,
) {
    ElevatedButton(
        onClick = onClick,
        modifier.padding(10.dp).applyIf(enabled) { pointerHoverIcon(PointerIcon.Hand) },
        enabled = enabled,
        border = border,
    ) {
        Text(text, fontSize = 50.sp, color = fontColor)
    }
}

@Composable
private fun MainMenuBackground() {
    Image(painterResource(Res.drawable.MainMenuBG), "mainMenuBackground", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

@Composable
private fun TitleText() {
    Image(painterResource(Res.drawable.TitleText), "titleText", Modifier.padding(100.dp).fillMaxWidth(fraction = 0.4f))
}