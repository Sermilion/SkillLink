package skilllink.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val sidebarWidth = 260.dp
private val panelPadding = 28.dp
private val contentSpacing = 16.dp
private const val LIBRARY_BLUE = 0xFF245C73
private const val CANVAS_GREY = 0xFFF7F9FA
private const val PANEL_WHITE = 0xFFFFFFFF
private const val SIDEBAR_BLUE = 0xFFE7EFF3
private const val INK_BLUE = 0xFF20343E
private val libraryColors =
    lightColorScheme(
        primary = Color(LIBRARY_BLUE),
        background = Color(CANVAS_GREY),
        surface = Color(PANEL_WHITE),
        surfaceVariant = Color(SIDEBAR_BLUE),
        onSurface = Color(INK_BLUE),
    )

@Composable
fun SkillLinkApp() {
    MaterialTheme(colorScheme = libraryColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row {
                LibrarySidebar()
                Column(
                    modifier = Modifier.padding(panelPadding),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing),
                ) {
                    Text("One library. Every agent.", style = MaterialTheme.typography.headlineLarge)
                    Text("Keep a shared copy of each skill for Claude, Codex, Junie, and Cursor.")
                    Text("Skill import and editing are coming in a future version.")
                }
            }
        }
    }
}

@Composable
private fun LibrarySidebar() {
    Surface(
        modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(panelPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            Text("SkillLink", style = MaterialTheme.typography.headlineMedium)
            Text("Your skills", style = MaterialTheme.typography.titleMedium)
            Text("No managed skills yet.")
        }
    }
}
