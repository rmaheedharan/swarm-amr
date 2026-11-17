package com.swarmamr.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class SetupStep(
    val title: String,
    val status: String,
    val done: Boolean,
    val progress: Float? = null,
    val optional: Boolean = false,
    val open: () -> Unit,
)

/** The Set up tab: the steps to prepare a place, in order, with the next one highlighted. */
@Composable
fun SetupScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val host = LocalHost.current
    val snackHost = LocalSnack.current
    val site by app.sites.site.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val wifi by app.runtime.wifi.collectAsStateWithLifecycle()
    var confirmDownload by remember { mutableStateOf(false) }

    val g = site.grid
    val n = site.markers.size
    val surveyed = site.markers.count { site.surveyed(it) }
    val open = g.openFloor
    val steps = buildList {
        add(
            SetupStep(
                "Floor plan",
                if (settings.floorPlanSaved) "${g.nAisles} aisles, ${m(g.aisleLenCells * g.cellM, 0)} long" else "Draw the aisles or walkways",
                settings.floorPlanSaved,
            ) { nav.push(Screen.FloorPlan) },
        )
        add(
            SetupStep(
                "Markers",
                when {
                    n > 0 -> "$n spots. Tap to add desk QR codes or names."
                    open -> "Optional. Spots are added when you record Wi-Fi."
                    else -> "Add the spots you will stand on"
                },
                n > 0,
                optional = open,
            ) { nav.push(Screen.Markers) },
        )
        add(
            SetupStep(
                "Teach Wi-Fi",
                when {
                    open -> if (surveyed == 0) "Tap the map where you stand, then record" else "$surveyed spots recorded. Add more for better accuracy."
                    n == 0 -> "Walk to each spot and record Wi-Fi"
                    else -> "$surveyed of $n spots recorded"
                },
                if (open) surveyed > 0 else n > 0 && surveyed == n,
                progress = if (!open && n > 0 && surveyed in 1 until n) surveyed / n.toFloat() else null,
            ) { nav.push(if (open || n == 0) Screen.QuickRecord else Screen.Survey) },
        )
        if (wifi.rttSupported || site.accessPoints.isNotEmpty()) {
            add(
                SetupStep(
                    "Access points",
                    if (site.accessPoints.isEmpty()) "Optional. For Wi-Fi RTT, the most accurate mode." else "${site.accessPoints.size} on the map",
                    site.accessPoints.isNotEmpty(),
                    optional = true,
                ) { nav.push(Screen.AccessPoints) },
            )
        }
    }
    val next = steps.firstOrNull { !it.done && !it.optional }

    AppScaffold(
        title = "Set up",
        actions = {
            OverflowMenu(
                listOf(
                    MenuAction("Share place file") {
                        host.share(app.tools.exportSite(), "application/json", listOfNotNull(app.tools.exportPlanImage()))
                    },
                    MenuAction("Open place file") { host.importPlace() },
                    MenuAction("Send place to server") {
                        app.scope.launchUpload(snackHost, "Place sent to server") { app.sites.upload(settings.backendUrl) }
                    },
                    MenuAction("Get place from server") { confirmDownload = true },
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = CardGap,
        ) {
            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(site.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Do these steps once for each place.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(steps) { i, st -> StepCard(i + 1, st, highlight = st === next) }
            item {
                OutlinedCard(onClick = { nav.push(Screen.QuickRecord) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AddLocationAlt, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Record Wi-Fi anywhere", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap the map where you stand, or scan a code, then record. No need to add markers first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (next == null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Set up is done", style = MaterialTheme.typography.titleMedium)
                            Text("Now check how accurate it is.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { nav.goTab(Tab.TEST) }) { Text("Go to Test") }
                        }
                    }
                }
            }
        }
    }

    if (confirmDownload) {
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("Get place from server?") },
            text = { Text("This replaces the floor plan, markers and Wi-Fi recordings on this phone with the ones on ${settings.backendUrl}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownload = false
                    app.scope.launchUpload(snackHost, "Place downloaded") { app.sites.download(settings.backendUrl, settings.siteId).map { } }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Cancel") } },
        )
    }
}

/** Run a server call and tell the user how it went. */
fun CoroutineScope.launchUpload(host: SnackbarHostState, okText: String, call: suspend () -> Result<Unit>) {
    launch {
        val r = call()
        snack(host, r.fold({ okText }, { "Could not reach the server: ${it.message ?: "error"}. Check the address in Settings." }))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepCard(n: Int, step: SetupStep, highlight: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = step.open,
        modifier = Modifier.fillMaxWidth(),
        colors = if (highlight) CardDefaults.cardColors(containerColor = scheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(
                    when {
                        step.done -> GoodGreen
                        highlight -> scheme.primary
                        else -> scheme.surfaceVariant
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (step.done) Icon(Icons.Filled.Check, "Done", tint = Color.White)
                else Text("$n", color = if (highlight) scheme.onPrimary else scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (highlight) Text("Next step", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                Text(step.title, style = MaterialTheme.typography.titleMedium)
                Text(step.status, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                step.progress?.let { p ->
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
        }
    }
}
