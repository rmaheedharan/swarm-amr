package com.swarmamr.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.core.AccessPoint
import com.swarmamr.core.Vec2
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class Placing(val bssid: String, val name: String, val frequencyMhz: Int)

/** Put Wi-Fi RTT access points on the map, and calibrate their distance error. */
@Composable
fun AccessPointsScreen() {
    val app = LocalApp.current
    val host = LocalHost.current
    val snackHost = LocalSnack.current
    val scope = rememberCoroutineScope()
    val site by app.sites.site.collectAsStateWithLifecycle()
    val wifi by app.runtime.wifi.collectAsStateWithLifecycle()
    val running by app.runtime.running.collectAsStateWithLifecycle()
    val busy by app.tools.busy.collectAsStateWithLifecycle()
    var placing by remember { mutableStateOf<Placing?>(null) }
    var pickMarker by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val seen = wifi.seen.sortedWith(compareByDescending<com.swarmamr.app.sensing.SeenAp> { it.rttResponder }.thenByDescending { it.rssi })

    val tap: ((Vec2) -> Unit)? = placing?.let { p ->
        { pos: Vec2 ->
            val old = site.ap(p.bssid)
            app.sites.upsertAp(
                AccessPoint(p.bssid.lowercase(), pos.x, pos.y, p.name, old?.rttBiasM ?: 0.0, p.frequencyMhz.takeIf { it > 0 } ?: old?.frequencyMhz ?: 0),
            )
            placing = null
            scope.snack(snackHost, "Placed ${p.name}")
        }
    }

    AppScaffold(title = "Access points", back = true) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "Only needed for Wi-Fi RTT. Show the app where each RTT access point is, so it can measure the distance to it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            placing?.let { p ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Tap the map where ${p.name} is", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { placing = null }) { Text("Cancel") }
                        }
                    }
                }
            }
            item { FloorMap(site, showAccessPoints = true, onTap = tap) }
            val b = busy
            if (b != null) {
                item { RecordingPanel(b, site.marker(b.markerId)?.name ?: b.markerId, onCancel = { job?.cancel() }) }
            } else if (site.accessPoints.isNotEmpty() && site.markers.isNotEmpty()) {
                item {
                    OutlinedButton(onClick = { pickMarker = true }, enabled = running) { Text("Calibrate distances at a marker") }
                }
            }

            item { GroupHeader("On the map (${site.accessPoints.size})") }
            if (site.accessPoints.isEmpty()) item { Text("None yet. Choose one below.", style = MaterialTheme.typography.bodyMedium) }
            items(site.accessPoints, key = { "map-" + it.bssid }) { ap ->
                ListItem(
                    headlineContent = { Text(ap.label.ifBlank { ap.bssid }) },
                    supportingContent = {
                        Text("x ${num(ap.x)}, y ${num(ap.y)}" + if (ap.rttBiasM != 0.0) " · corrected ${m(ap.rttBiasM, 2)}" else "")
                    },
                    leadingContent = { Icon(Icons.Filled.Router, null) },
                    trailingContent = {
                        IconButton(onClick = { app.sites.removeAp(ap.bssid) }) { Icon(Icons.Filled.Delete, "Remove ${ap.label}") }
                    },
                    modifier = Modifier.clickable { placing = Placing(ap.bssid, ap.label.ifBlank { ap.bssid }, ap.frequencyMhz) },
                )
            }

            item { GroupHeader("Seen now (${seen.size})") }
            if (!running) {
                item {
                    WarningCard("Positioning is off", "Turn it on to see access points.", "Turn on") { host.startPositioning() }
                }
            }
            items(seen, key = { "seen-" + it.bssid }) { s ->
                val placed = site.ap(s.bssid) != null
                val name = s.ssid.ifBlank { "Hidden network" }
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text("${s.bssid} · ${s.rssi} dBm" + if (s.rttResponder) " · supports RTT" else "") },
                    leadingContent = {
                        Icon(Icons.Filled.Wifi, null, tint = if (s.rttResponder) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { Text(if (placed) "On map" else "Place", color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { placing = Placing(s.bssid, name, s.frequencyMhz) },
                )
            }
        }
    }

    if (pickMarker) {
        AlertDialog(
            onDismissRequest = { pickMarker = false },
            title = { Text("Where are you standing?") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Stand on a marker and choose it. The app measures for 15 seconds.", style = MaterialTheme.typography.bodyMedium)
                    site.markers.forEach { mk ->
                        ListItem(
                            headlineContent = { Text(mk.name) },
                            modifier = Modifier.clickable {
                                pickMarker = false
                                job = scope.launch {
                                    val bias = app.tools.calibrateRtt(mk)
                                    scope.snack(
                                        snackHost,
                                        if (bias.isEmpty()) "No RTT readings. Check that the access points are placed and in range."
                                        else "Corrected ${bias.size} access points",
                                    )
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickMarker = false }) { Text("Cancel") } },
        )
    }
}
