package com.swarmamr.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swarmamr.app.Busy
import kotlinx.coroutines.delay

/** One entry of a top-bar overflow menu. */
class MenuAction(val label: String, val onClick: () -> Unit)

/** The three-dot menu at the right of the top bar. */
@Composable
fun OverflowMenu(items: List<MenuAction>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { a ->
                DropdownMenuItem(text = { Text(a.label) }, onClick = { open = false; a.onClick() })
            }
        }
    }
}

/** A yellow card for a problem the user can fix, with one button that fixes it. */
@Composable
fun WarningCard(title: String, body: String, action: String?, modifier: Modifier = Modifier, onAction: () -> Unit = {}) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                if (action != null) {
                    TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
                }
            }
        }
    }
}

/** Keeps the screen on while shown (during a recording). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** A countdown ring with "stand still" text while a recording runs. */
@Composable
fun RecordingPanel(busy: Busy, placeName: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(busy) {
        while (true) { now = System.currentTimeMillis(); delay(100) }
    }
    KeepScreenOn()
    ElevatedCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { busy.progress(now) }, modifier = Modifier.fillMaxSize(), strokeWidth = 6.dp)
                Text("${busy.secondsLeft(now)}", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("${busy.what} at $placeName", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (busy.what == "Learning Wi-Fi") "Hold the phone in front of you and turn around slowly, once."
                    else "Stand still. Hold the phone in front of you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** Label on the left, a value between minus and plus buttons on the right. */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, "Less") }
        Text(value, Modifier.widthIn(min = 76.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Filled.Add, "More") }
    }
}

/** Small coloured heading above a group of list rows. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A settings-style row: title, optional value under it. */
@Composable
fun InfoRow(title: String, value: String, modifier: Modifier = Modifier) {
    val sub: (@Composable () -> Unit)? = if (value.isEmpty()) null else { { Text(value) } }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub,
        modifier = modifier,
    )
}

/** Space between items in a column of cards. */
val CardGap = Arrangement.spacedBy(12.dp)

/** Warns when two recorded spots far apart have almost the same Wi-Fi, so the app may mix them up. */
@Composable
fun LookAlikeWarning(site: com.swarmamr.core.Site, modifier: Modifier = Modifier) {
    val pairs = androidx.compose.runtime.remember(site) { com.swarmamr.core.RssiPositioning.lookAlikes(site.fingerprints) }
    if (pairs.isEmpty()) return
    fun name(id: String) = site.marker(id)?.name ?: id
    val list = pairs.take(2).joinToString("; ") { (a, b, _) ->
        "${name(a.markerId)} and ${name(b.markerId)} (${m(a.pos.dist(b.pos), 0)} apart)"
    }
    WarningCard(
        if (pairs.size == 1) "Two spots look alike" else "${pairs.size} pairs of spots look alike",
        "$list have almost the same Wi-Fi, so the app may mix them up. " +
            "Record them again while turning around slowly, and add a spot between them.",
        null,
        modifier,
    )
}
