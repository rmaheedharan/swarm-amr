package com.swarmamr.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.core.Coverage
import com.swarmamr.core.MapMatcher
import com.swarmamr.core.Marker
import com.swarmamr.core.Vec2
import com.swarmamr.core.WalkRecording
import com.swarmamr.app.Walking
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Record Wi-Fi anywhere, in one step: tap the map where you stand (or scan the code there),
 * then Record. The spot is saved as a marker at the same time, so there is no need to add
 * markers first. Works on any floor, also one with no fixed walkways.
 */
@Composable
fun QuickRecordScreen() {
    val app = LocalApp.current
    val host = LocalHost.current
    val nav = LocalNav.current
    val scanner = LocalScan.current
    val snackHost = LocalSnack.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val site by app.sites.site.collectAsStateWithLifecycle()
    val busy by app.tools.busy.collectAsStateWithLifecycle()
    val running by app.runtime.running.collectAsStateWithLifecycle()
    val est by app.runtime.estimate.collectAsStateWithLifecycle()
    val walking by app.tools.walking.collectAsStateWithLifecycle()
    var showCover by remember { mutableStateOf(true) }
    val coverage = remember(site, showCover) { if (showCover) Coverage.of(site) else null }
    DisposableEffect(Unit) { onDispose { app.tools.cancelWalk() } }

    val mapState = rememberMapState()
    var pin by remember { mutableStateOf<Vec2?>(null) }
    var code by remember { mutableStateOf("") }
    var chosenId by remember { mutableStateOf<String?>(null) }
    var typePos by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val recorded = remember(site) { site.markers.filter { site.surveyed(it) }.map { it.id }.toSet() }
    val matcher = remember(site.grid) { MapMatcher(site.grid) }

    fun nextName(): String {
        var i = site.markers.size + 1
        while (site.marker(String.format(Locale.US, "P%03d", i)) != null) i++
        return String.format(Locale.US, "P%03d", i)
    }

    fun choose(mk: Marker) { pin = mk.pos; code = mk.id; chosenId = mk.id }

    fun place(p: Vec2) {
        pin = matcher.snapRaw(p).pos
        // A tap makes a new spot. Keep a code the user scanned or typed for it.
        if (chosenId != null || code.isBlank()) code = nextName()
        chosenId = null
    }

    fun startWalk() {
        val p = pin ?: return
        app.tools.startWalk(scope, p)
        pin = null; code = ""; chosenId = null
    }

    fun finishWalk(at: Vec2) {
        val fps = app.tools.finishWalk(matcher.snapRaw(at).pos)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (fps.isEmpty()) {
            scope.snack(snackHost, "No Wi-Fi readings on this walk. Walk more slowly, and check that Wi-Fi is on.")
        } else {
            val name = fps.first().markerId.substringBefore('.')
            scope.snack(snackHost, "Saved walk $name: ${fps.size} spots along the line", "Undo") {
                app.sites.removeFingerprints(fps.map { it.markerId }.toSet())
            }
        }
    }

    fun record() {
        val p = pin ?: return
        if (job?.isActive == true) return
        val id = code.trim().ifEmpty { nextName() }
        val other = site.marker(id)
        if (other != null && other.id != chosenId) {
            scope.snack(snackHost, "\"$id\" is already used by another spot. Tap it on the map to record it again.")
            return
        }
        val old = chosenId?.let { site.marker(it) }
        val mk = Marker(id, p.x, p.y, old?.note ?: "", old?.label ?: "")
        val oldId = chosenId
        job = scope.launch {
            val n = app.tools.recordFingerprint(mk, WalkKind.SURVEY.seconds)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (n == 0) {
                scope.snack(snackHost, "No Wi-Fi readings. Check that Wi-Fi and location are on.")
            } else {
                app.sites.upsertMarker(mk, oldId = oldId)
                scope.snack(snackHost, "Saved ${mk.name} ($n Wi-Fi readings). Tap the next spot.")
                pin = null; code = ""; chosenId = null
            }
        }
    }

    OnScan { id ->
        if (busy != null || walking != null) return@OnScan
        val mk = site.marker(id)
        if (mk != null) {
            choose(mk)
            if (running) record()
        } else {
            code = id; chosenId = null
            if (pin == null) scope.snack(snackHost, "New code \"$id\". Now tap the map where you stand.")
        }
    }

    AppScaffold(
        title = "Record Wi-Fi",
        back = true,
        actions = {
            IconButton(onClick = { showCover = !showCover }) {
                Icon(if (showCover) Icons.Filled.Layers else Icons.Outlined.Layers, if (showCover) "Hide coverage" else "Show coverage")
            }
            IconButton(onClick = { scanner.camera("Scan the code where you stand") }) { Icon(Icons.Filled.QrCodeScanner, "Scan code") }
            val lastWalk = site.fingerprints.map { it.markerId }.filter { WalkRecording.isWalk(it) }
                .maxByOrNull { it.substringAfter('W').substringBefore('.').toIntOrNull() ?: 0 }?.substringBefore('.')
            OverflowMenu(
                listOfNotNull(
                    lastWalk?.let { w ->
                        MenuAction("Remove walk $w") {
                            app.sites.removeFingerprints(site.fingerprints.map { it.markerId }.filter { it.startsWith("$w.") }.toSet())
                        }
                    },
                    MenuAction("Floor plan picture") { nav.push(Screen.FloorPlan) },
                ),
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            FloorMap(
                site,
                me = est,
                pin = pin,
                done = recorded,
                highlight = chosenId,
                state = mapState,
                fill = true,
                coverage = coverage,
                line = walking?.let { it.from to null },
                onTap = { p -> if (walking != null) finishWalk(p) else if (busy == null) place(p) },
                onMarkerTap = { mk -> if (walking != null) finishWalk(mk.pos) else if (busy == null) choose(mk) },
            )

            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!running) {
                    WarningCard("Positioning is off", "The app needs it to read Wi-Fi.", "Turn on") { host.startPositioning() }
                } else if (host.scanThrottleOn() == true) {
                    WarningCard(
                        "Android is slowing Wi-Fi scans",
                        "Recordings will be weak. Turn off \"Wi-Fi scan throttling\" in Developer options.",
                        "Fix this",
                    ) { host.openDeveloperOptions() }
                }
                if (pin == null && busy == null && walking == null) LookAlikeWarning(site)
                if (coverage != null && pin == null && busy == null && walking == null) CoverageLegend(coverage)
                if (pin == null && busy == null && walking == null) {
                    ElevatedCard(Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (site.image != null) "Tap the plan where you stand" else "Tap the map where you stand",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Or scan the code at your spot. Green spots are recorded: ${recorded.size} so far. " +
                                    "Tap a spot to record it again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().padding(12.dp)) {
                val b = busy
                val p = pin
                val w = walking
                when {
                    w != null -> WalkingPanel(w, onCancel = { app.tools.cancelWalk() })
                    b != null -> RecordingPanel(b, site.marker(b.markerId)?.name ?: b.markerId, onCancel = { job?.cancel() })
                    p != null -> ElevatedCard(Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                code, { code = it },
                                label = { Text("Code or name of this spot") },
                                trailingIcon = {
                                    IconButton(onClick = { scanner.camera("Scan the code where you stand") }) {
                                        Icon(Icons.Filled.QrCodeScanner, "Scan code")
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "x ${m(p.x)}, y ${m(p.y)}",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { typePos = true }) { Text("Type position") }
                            }
                            Text(
                                (if (chosenId != null) "Recording again replaces the old recording. Stand still for 20 seconds."
                                else "Record here: stand on the spot and turn around slowly, once. It takes 20 seconds.") +
                                    " Walk: record while you walk in a straight line from here, then tap where you stop.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { pin = null; code = ""; chosenId = null }) { Text("Cancel") }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(onClick = { startWalk() }, enabled = running, modifier = Modifier.height(52.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Walk")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { record() }, enabled = running, modifier = Modifier.height(52.dp)) {
                                    Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Record")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (typePos) {
        var xText by remember { mutableStateOf(pin?.let { num(it.x, 2) } ?: "") }
        var yText by remember { mutableStateOf(pin?.let { num(it.y, 2) } ?: "") }
        val x = parseNum(xText)
        val y = parseNum(yText)
        AlertDialog(
            onDismissRequest = { typePos = false },
            title = { Text("Type position") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Metres from the corner of the floor (bottom left of the map).", style = MaterialTheme.typography.bodyMedium)
                    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    OutlinedTextField(xText, { xText = it }, label = { Text("x (m), to the right") }, singleLine = true, keyboardOptions = decimal)
                    OutlinedTextField(yText, { yText = it }, label = { Text("y (m), up the map") }, singleLine = true, keyboardOptions = decimal)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    typePos = false
                    if (x != null && y != null) {
                        pin = Vec2(x, y)
                        if (chosenId != null || code.isBlank()) code = nextName()
                        chosenId = null
                    }
                }, enabled = x != null && y != null) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { typePos = false }) { Text("Cancel") } },
        )
    }
}

/** Shown while a walk-and-record runs. */
@Composable
private fun WalkingPanel(w: Walking, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(w.startMs) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    KeepScreenOn()
    ElevatedCard(Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Walking and recording", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${(now - w.startMs) / 1000} s", style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Walk slowly in a straight line, phone in front of you. When you stop, tap the map where you are. " +
                    "Wi-Fi readings so far: ${w.scans}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

/** What the coverage colours mean, and how much of the floor is covered. */
@Composable
private fun CoverageLegend(c: Coverage.Grid) {
    ElevatedCard(Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${(c.covered * 100).roundToInt()}% covered",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            LegendDot(Color(0xFF43A047), "Good")
            Spacer(Modifier.width(10.dp))
            LegendDot(Color(0xFF9E9E9E), "None")
            Spacer(Modifier.width(10.dp))
            LegendDot(Color(0xFFE53935), "Look alike")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
