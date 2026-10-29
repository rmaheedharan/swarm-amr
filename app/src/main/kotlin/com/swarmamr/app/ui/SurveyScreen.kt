package com.swarmamr.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.app.SwarmApp
import com.swarmamr.app.sensing.SeenAp
import com.swarmamr.core.AccessPoint
import com.swarmamr.core.GridSpec
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SurveyScreen(app: SwarmApp, markerFromQr: String?, onScanQr: () -> Unit, onImportSite: () -> Unit) {
    val ctx = LocalContext.current
    val site by app.sites.site.collectAsStateWithLifecycle()
    val wifi by app.runtime.wifi.collectAsStateWithLifecycle()
    val est by app.runtime.estimate.collectAsStateWithLifecycle()
    val busy by app.tools.busy.collectAsStateWithLifecycle()
    val running by app.runtime.running.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf("") }
    var markerId by remember { mutableStateOf("") }
    var editAp by remember { mutableStateOf<SeenAp?>(null) }
    var editGrid by remember { mutableStateOf(false) }
    var onlyRtt by remember { mutableStateOf(false) }
    LaunchedEffect(markerFromQr) { markerFromQr?.let { markerId = it } }
    val marker = site.marker(markerId)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        Section("Site map") {
            KeyValue("Name", site.name)
            KeyValue("Site id / version", "${site.id} / ${site.version}")
            KeyValue("Aisles", "${site.grid.nAisles} (narrow: ${site.grid.narrowAisles.joinToString()})")
            KeyValue("Access points placed", site.accessPoints.size.toString())
            KeyValue("Floor markers", site.markers.size.toString())
            KeyValue("Wi-Fi fingerprints", site.fingerprints.size.toString())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    app.scope.launch {
                        message = app.sites.download(settings.backendUrl, settings.siteId)
                            .fold({ "Downloaded site ${it.id} v${it.version}." }, { "Download failed: ${it.message}" })
                    }
                }) { Text("Get from server") }
                OutlinedButton(onClick = {
                    app.scope.launch {
                        message = app.sites.upload(settings.backendUrl).fold({ "Uploaded." }, { "Upload failed: ${it.message}" })
                    }
                }) { Text("Send to server") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { share(ctx, app.tools.exportSite(), "application/json") }) { Text("Export file") }
                OutlinedButton(onClick = onImportSite) { Text("Import file") }
                TextButton(onClick = { editGrid = true }) { Text("Edit layout") }
            }
            if (message.isNotBlank()) Text(message)
        }

        Section("Stand on a marker and record") {
            Text(
                "Fingerprints teach the app what Wi-Fi looks like at each marker (needed when access points " +
                    "do not support RTT). RTT calibration measures each access point's distance error.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = markerId, onValueChange = { markerId = it.trim() }, singleLine = true,
                label = { Text("Marker") }, modifier = Modifier.fillMaxWidth(),
                isError = markerId.isNotBlank() && marker == null,
            )
            OutlinedButton(onClick = onScanQr) { Text("Scan QR") }
            val b = busy
            if (b != null) {
                Text("${b.what} at ${b.markerId}… stand still")
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = running && marker != null, onClick = {
                        val mk = marker ?: return@Button
                        app.scope.launch {
                            val n = app.tools.recordFingerprint(mk)
                            message = if (n == 0) "No Wi-Fi scans arrived. See the tip in Settings about scan throttling."
                            else "Fingerprint saved at ${mk.id} from $n scans."
                        }
                    }) { Text("Fingerprint 20 s") }
                    OutlinedButton(enabled = running && marker != null && wifi.rttSupported, onClick = {
                        val mk = marker ?: return@OutlinedButton
                        app.scope.launch {
                            val bias = app.tools.calibrateRtt(mk)
                            message = if (bias.isEmpty()) "Not enough RTT data. Are RTT access points placed on the map?"
                            else "Bias saved: " + bias.entries.joinToString { "${it.key.takeLast(5)} ${f1(it.value)} m" }
                        }
                    }) { Text("Calibrate RTT 15 s") }
                }
                if (!running) Text("Start positioning on the Live tab first.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(enabled = site.fingerprints.isNotEmpty(), onClick = { app.sites.clearFingerprints() }) {
                Text("Delete all fingerprints")
            }
        }

        Section("Access points seen now") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = onlyRtt, onCheckedChange = { onlyRtt = it })
                Text("Only RTT-capable")
            }
            val list = wifi.seen.filter { !onlyRtt || it.rttResponder }
            if (list.isEmpty()) Text(if (running) "None yet. Wait for a Wi-Fi scan." else "Start positioning to scan.")
            list.take(40).forEach { ap ->
                val placed = site.ap(ap.bssid)
                Column(Modifier.fillMaxWidth().clickable { editAp = ap }.padding(vertical = 6.dp)) {
                    Text(
                        "${ap.ssid.ifBlank { "(hidden)" }}  ${ap.rssi} dBm" + if (ap.rttResponder) "  · RTT" else "",
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${ap.bssid} · ${ap.frequencyMhz} MHz · " +
                            (placed?.let { "placed at ${f1(it.x)}, ${f1(it.y)}" } ?: "tap to place on map"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    editAp?.let { ap ->
        ApDialog(
            ap = ap,
            placed = site.ap(ap.bssid),
            here = est?.snapped?.pos?.let { it.x to it.y },
            markerPos = marker?.let { it.x to it.y },
            onSave = { x, y, label ->
                app.sites.upsertAp(
                    AccessPoint(ap.bssid, x, y, label, site.ap(ap.bssid)?.rttBiasM ?: 0.0, ap.frequencyMhz),
                )
                editAp = null
            },
            onRemove = { app.sites.removeAp(ap.bssid); editAp = null },
            onDismiss = { editAp = null },
        )
    }
    if (editGrid) GridDialog(site.grid, onSave = { g, regen -> app.sites.setGrid(g, regen); editGrid = false }, onDismiss = { editGrid = false })
}

private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)

@Composable
private fun ApDialog(
    ap: SeenAp,
    placed: AccessPoint?,
    here: Pair<Double, Double>?,
    markerPos: Pair<Double, Double>?,
    onSave: (Double, Double, String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var x by remember { mutableStateOf(placed?.x?.let(::f1) ?: "") }
    var y by remember { mutableStateOf(placed?.y?.let(::f1) ?: "") }
    var label by remember { mutableStateOf(placed?.label ?: ap.ssid) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Place access point") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${ap.bssid}${if (ap.rttResponder) " (RTT)" else ""}")
                Text("Enter where it hangs, in metres from the site origin. Measure with a tape or laser.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(x, { x = it }, label = { Text("x (m)") }, singleLine = true)
                OutlinedTextField(y, { y = it }, label = { Text("y (m)") }, singleLine = true)
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    markerPos?.let { (mx, my) -> TextButton(onClick = { x = f1(mx); y = f1(my) }) { Text("At marker") } }
                    here?.let { (hx, hy) -> TextButton(onClick = { x = f1(hx); y = f1(hy) }) { Text("Where I am") } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val xv = x.replace(',', '.').toDoubleOrNull()
                val yv = y.replace(',', '.').toDoubleOrNull()
                if (xv != null && yv != null) onSave(xv, yv, label)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (placed != null) TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun GridDialog(g: GridSpec, onSave: (GridSpec, Boolean) -> Unit, onDismiss: () -> Unit) {
    var nAisles by remember { mutableStateOf(g.nAisles.toString()) }
    var len by remember { mutableStateOf(g.aisleLenCells.toString()) }
    var pitch by remember { mutableStateOf(f1(g.aislePitchM)) }
    var cell by remember { mutableStateOf(f1(g.cellM)) }
    var ox by remember { mutableStateOf(f1(g.originX)) }
    var oy by remember { mutableStateOf(f1(g.originY)) }
    var narrow by remember { mutableStateOf(g.narrowAisles.joinToString(",")) }
    var regen by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Racking layout") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Aisles are numbered from 0, left to right.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(nAisles, { nAisles = it }, label = { Text("Number of aisles") }, singleLine = true)
                OutlinedTextField(len, { len = it }, label = { Text("Aisle length (cells)") }, singleLine = true)
                OutlinedTextField(cell, { cell = it }, label = { Text("Cell length along aisle (m)") }, singleLine = true)
                OutlinedTextField(pitch, { pitch = it }, label = { Text("Aisle to aisle distance (m)") }, singleLine = true)
                OutlinedTextField(ox, { ox = it }, label = { Text("Origin x (m)") }, singleLine = true)
                OutlinedTextField(oy, { oy = it }, label = { Text("Origin y (m)") }, singleLine = true)
                OutlinedTextField(narrow, { narrow = it }, label = { Text("Narrow aisles, e.g. 1,2,5") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(regen, { regen = it }); Text("Re-make floor markers")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fun d(s: String) = s.replace(',', '.').toDoubleOrNull()
                val ng = GridSpec(
                    originX = d(ox) ?: g.originX, originY = d(oy) ?: g.originY,
                    aislePitchM = d(pitch) ?: g.aislePitchM, cellM = d(cell) ?: g.cellM,
                    nAisles = nAisles.toIntOrNull()?.coerceIn(1, 60) ?: g.nAisles,
                    aisleLenCells = len.toIntOrNull()?.coerceIn(2, 200) ?: g.aisleLenCells,
                    narrowAisles = narrow.split(',', ' ').mapNotNull { it.trim().toIntOrNull() },
                )
                onSave(ng, regen)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
