package skilllink.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import skilllink.desktop.SkillLinkApp

private val initialWindowWidth = 1000.dp
private val initialWindowHeight = 680.dp

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "SkillLink",
            state = rememberWindowState(width = initialWindowWidth, height = initialWindowHeight),
        ) {
            SkillLinkApp()
        }
    }
