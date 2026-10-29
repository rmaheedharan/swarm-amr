package com.swarmamr.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.app.SwarmApp
import com.swarmamr.core.Accuracy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AccuracyScreen(app: SwarmApp, markerFromQr: String?, onScanQr: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tools = app.tools
    val samples by tools.samples.collectAsStateWithLifecycle()
    val busy by tools.busy.collectAsStateWithLifecycle()
    val site by app.sites.site.collectAsStateWithLifecycle()
    val est by app.runtime.estimate.collectAsStateWithLifecycle()
    val running by app.runtime.running.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    var markerId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(markerFromQr) { markerFromQr?.let { markerId = it } }
    LaunchedEffect(busy) { while (busy != null) { now = System.currentTimeMillis(); delay(200) } }
    val summary = remember(samples) { Accuracy.summarise(samples) }
    val marker = site.marker(markerId.trim())

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        Section("Result so far") {
            if (summary.samples == 0) {
                Text("No measurements yet. Stand on a floor marker, choose it below, and press Record.")
            } else {
                KeyValue("Samples / markers", "${summary.samples} / ${summary.markers}")
                KeyValue("Median error (p50)", m(summary.p50M))
                KeyValue("90% of errors below (p90)", m(summary.p90M))
                KeyValue("Average / worst", "${m(summary.meanM)} / ${m(summary.maxM)}")
                KeyValue("Correct aisle", pct(summary.correctAislePct))
                KeyValue("Sources", summary.bySource.entries.joinToString { "${it.key} ${it.value}" })
                Text(
                    if (summary.passesGate1) "PASS: meets the week 2 target (95% right aisle, p90 2 m or less)"
                    else "Not yet at the week 2 target (95% right aisle, p90 2 m or less)",
                    color = if (summary.passesGate1) Color(0xFF2E7D32) else Color(0xFFB26A00),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Section("Measure at a marker") {
            Text("1. Stand on a floor marker. 2. Scan its QR label or type its name. 3. Press Record and stand still for 10 s.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = markerId, onValueChange = { markerId = it.trim() },
                label = { Text("Marker (for example A3-07)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = markerId.isNotBlank() && marker == null,
                supportingText = {
                    Text(marker?.let { "At x ${m(it.x)}, y ${m(it.y)}" } ?: if (markerId.isBlank()) "" else "Not in the site map")
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScanQr) { Text("Scan QR") }
                est?.let { e ->
                    val near = site.markers.sortedBy { it.pos.dist(e.snapped.pos) }.take(3)
                    near.forEach { mk -> AssistChip(onClick = { markerId = mk.id }, label = { Text(mk.id) }) }
                }
            }
            val b = busy
            if (b != null) {
                Text("${b.what} at ${b.markerId}… stand still")
                val left = ((b.untilMs - now) / 1000.0).coerceAtLeast(0.0)
                LinearProgressIndicator(progress = { (1f - (left / 10.0).toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                Button(
                    enabled = running && marker != null,
                    onClick = {
                        val mk = marker ?: return@Button
                        app.scope.launch {
                            val n = tools.recordAccuracy(mk)
                            message = if (n == 0) "No position during the test. Is positioning running?" else "Saved $n samples at ${mk.id}."
                        }
                    },
                ) { Text("Record 10 s here") }
                if (!running) Text("Start positioning on the Live tab first.", style = MaterialTheme.typography.bodySmall)
            }
            if (message.isNotBlank()) Text(message)
        }

        Section("Per marker") {
            val byMarker = samples.groupBy { it.markerId }
            if (byMarker.isEmpty()) Text("Nothing yet.")
            byMarker.entries.sortedBy { it.key }.forEach { (id, list) ->
                val mean = list.map { it.errorM }.average()
                val ok = 100.0 * list.count { it.aisleCorrect } / list.size
                KeyValue(id, "${list.size} samples · ${m(mean)} · aisle ${pct(ok)}")
            }
        }

        Section("Share results") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = samples.isNotEmpty(), onClick = { share(ctx, tools.exportCsv(), "text/csv") }) { Text("Export CSV") }
                OutlinedButton(enabled = samples.isNotEmpty(), onClick = {
                    scope.launch {
                        message = tools.uploadSamples(settings.backendUrl).fold({ "Uploaded to the server." }, { "Upload failed: ${it.message}" })
                    }
                }) { Text("Send to server") }
            }
            TextButton(enabled = samples.isNotEmpty(), onClick = { confirmClear = true }) { Text("Clear all measurements") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all measurements?") },
            text = { Text("Export the CSV first if you want to keep them.") },
            confirmButton = { TextButton(onClick = { tools.clearSamples(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

fun share(ctx: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
