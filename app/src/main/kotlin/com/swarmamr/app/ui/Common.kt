package com.swarmamr.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swarmamr.app.swarm.UiCue
import java.util.Locale

@Composable
fun SwarmTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(primary = Color(0xFF8AB4F8), secondary = Color(0xFFFFD166))
        } else {
            lightColorScheme(primary = Color(0xFF2F5D8C), secondary = Color(0xFFB5832A))
        },
        content = content,
    )
}

/** Banner colours. Colour is never the only signal: each state also has its own word. */
fun cueColor(c: UiCue): Color = when (c) {
    UiCue.NORMAL -> Color(0xFF2E7D32)
    UiCue.WARNING -> Color(0xFFE09B00)
    UiCue.HOLD -> Color(0xFF1565C0)
    UiCue.GO -> Color(0xFF43A047)
    UiCue.REROUTE, UiCue.RESEQUENCE -> Color(0xFF6A1B9A)
    UiCue.OFFLINE -> Color(0xFF616161)
}

fun cueWord(c: UiCue): String = when (c) {
    UiCue.NORMAL -> "Normal"
    UiCue.WARNING -> "Slow"
    UiCue.HOLD -> "Wait"
    UiCue.GO -> "Go"
    UiCue.REROUTE -> "Reroute"
    UiCue.RESEQUENCE -> "New order"
    UiCue.OFFLINE -> "Swarm off"
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

fun m(v: Double, digits: Int = 1) = String.format(Locale.US, "%.${digits}f m", v)
fun pct(v: Double) = String.format(Locale.US, "%.0f%%", v)

/** QR labels on the floor carry "SWM:<marker id>"; a plain marker id is accepted too. */
fun parseMarkerQr(text: String?): String? = text?.trim()?.removePrefix("SWM:")?.takeIf { it.isNotBlank() }
