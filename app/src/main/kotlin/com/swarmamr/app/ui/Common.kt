package com.swarmamr.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.swarmamr.app.swarm.UiCue
import java.io.File
import java.util.Locale

/** Material 3 with Material You colours from the wallpaper on Android 12+. */
@Composable
fun SwarmTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFFA8C8FF), secondary = Color(0xFFBDC7DC), tertiary = Color(0xFF7DD5C8))
        else -> lightColorScheme(primary = Color(0xFF2F5D8C), secondary = Color(0xFF555F71), tertiary = Color(0xFF006A62))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Colours that keep their meaning in any theme: good, needs work. */
val GoodGreen = Color(0xFF2E7D32)
val WarnAmber = Color(0xFFB26A00)

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
    UiCue.NORMAL -> "All clear"
    UiCue.WARNING -> "Slow down"
    UiCue.HOLD -> "Wait"
    UiCue.GO -> "Go"
    UiCue.REROUTE -> "Take another way"
    UiCue.RESEQUENCE -> "Next pick changed"
    UiCue.OFFLINE -> "No traffic info"
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A centred icon, title and one line of help: what to do when a list is empty. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
    }
}

fun m(v: Double, digits: Int = 1) = String.format(Locale.US, "%.${digits}f m", v)
fun num(v: Double, digits: Int = 1) = String.format(Locale.US, "%.${digits}f", v)
fun pct(v: Double) = String.format(Locale.US, "%.0f%%", v)
fun parseNum(s: String) = s.trim().replace(',', '.').toDoubleOrNull()

fun share(ctx: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
