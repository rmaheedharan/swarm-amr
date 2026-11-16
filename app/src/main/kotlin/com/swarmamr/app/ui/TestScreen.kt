package com.swarmamr.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.core.Accuracy
import com.swarmamr.core.AccuracySummary

/** The Test tab: accuracy results in plain words, and the button to (continue the) test walk. */
@Composable
fun TestScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val host = LocalHost.current
    val snackHost = LocalSnack.current
    val site by app.sites.site.collectAsStateWithLifecycle()
    val samples by app.tools.samples.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val summary = remember(samples) { Accuracy.summarise(samples) }
    val perSpot = remember(samples, site) {
        samples.groupBy { it.markerId }.map { (id, list) ->
            val errs = list.map { it.errorM }.sorted()
            Triple(site.marker(id)?.name ?: id, errs[errs.size / 2], list.count { it.aisleCorrect } * 100.0 / list.size)
        }.sortedByDescending { it.second }
    }

    AppScaffold(
        title = "Test",
        actions = {
            if (samples.isNotEmpty()) {
                IconButton(onClick = { host.share(app.tools.exportCsv(), "text/csv") }) { Icon(Icons.Filled.Share, "Share results (CSV)") }
                OverflowMenu(
                    listOf(
                        MenuAction("Send results to server") {
                            app.scope.launchUpload(snackHost, "Results sent to server") { app.tools.uploadSamples(settings.backendUrl) }
                        },
                        MenuAction("Clear results") { confirmClear = true },
                    ),
                )
            }
        },
        fab = {
            if (site.markers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { nav.push(Screen.TestWalk) },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text(if (samples.isEmpty()) "Start test" else "Continue test") },
                )
            }
        },
    ) { pad ->
        if (samples.isEmpty()) {
            EmptyState(
                Icons.Filled.Straighten, "How accurate is it here?",
                "Walk to each marker and tap Record. The app compares where it thinks you are with where you really are.",
                Modifier.padding(pad),
            ) {
                if (site.markers.isEmpty()) {
                    Button(onClick = { nav.goTab(Tab.SETUP); nav.push(Screen.Markers) }) { Text("Add markers first") }
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { VerdictCard(summary) }
                item { GroupHeader("By marker, worst first", Modifier.padding(top = 8.dp)) }
                items(perSpot) { (name, median, aislePct) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text("Right aisle ${pct(aislePct)} of the time") },
                        trailingContent = { Text("off by ${m(median)}", style = MaterialTheme.typography.titleSmall) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all results?") },
            text = { Text("Share them first if you want to keep them.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; app.tools.clearSamples() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun VerdictCard(s: AccuracySummary) {
    val good = s.passesGate1
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (good) scheme.secondaryContainer else scheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (good) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (good) GoodGreen else WarnAmber)
                Spacer(Modifier.width(8.dp))
                Text(if (good) "Good enough for traffic cues" else "Not good enough yet", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                if (good) "Measured on this floor."
                else "Teach Wi-Fi at more markers, turn off Wi-Fi scan throttling, or add access points with RTT.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(4.dp))
            KeyValue("Half of readings within", m(s.p50M))
            KeyValue("9 of 10 readings within", m(s.p90M))
            KeyValue("Right aisle", pct(s.correctAislePct))
            KeyValue("Markers tested", "${s.markers}")
            KeyValue("Readings", "${s.samples}")
            Text(
                "Target: 9 of 10 within 2 m, and right aisle 95% of the time.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
