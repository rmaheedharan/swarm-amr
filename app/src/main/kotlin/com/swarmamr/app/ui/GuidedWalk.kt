package com.swarmamr.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.core.Marker
import com.swarmamr.core.Vec2
import com.swarmamr.core.WalkPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class WalkKind(val title: String, val seconds: Int) {
    SURVEY("Teach Wi-Fi", 20),
    TEST("Accuracy test", 10),
}

/**
 * The guided walk, used for both the Wi-Fi survey and the accuracy test. The next marker pops
 * up; the user walks there and taps "I am here, record". A QR code is optional: scanning one
 * picks that marker and starts recording at once.
 */
@Composable
fun WalkScreen(kind: WalkKind) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val host = LocalHost.current
    val scanner = LocalScan.current
    val snackHost = LocalSnack.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val site by app.sites.site.collectAsStateWithLifecycle()
    val samples by app.tools.samples.collectAsStateWithLifecycle()
    val busy by app.tools.busy.collectAsStateWithLifecycle()
    val running by app.runtime.running.collectAsStateWithLifecycle()

    val done: Set<String> = remember(site, samples, kind) {
        val ids = if (kind == WalkKind.SURVEY) site.fingerprints.map { it.markerId }.toSet() else samples.map { it.markerId }.toSet()
        site.markers.map { it.id }.filter { it in ids }.toSet()
    }
    var skipped by remember { mutableStateOf(emptySet<String>()) }
    var lastPos by remember { mutableStateOf<Vec2?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val target: Marker? = chosen?.let { site.marker(it) } ?: WalkPlan.next(site.markers, done, lastPos, skipped)

    fun record(mk: Marker) {
        if (job?.isActive == true) return
        chosen = mk.id
        job = scope.launch {
            if (kind == WalkKind.SURVEY) {
                val n = app.tools.recordFingerprint(mk, kind.seconds)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (n == 0) {
                    scope.snack(snackHost, "No Wi-Fi readings at ${mk.name}. Check that Wi-Fi and location are on.")
                } else {
                    lastPos = mk.pos; chosen = null
                    scope.snack(snackHost, "Saved ${mk.name} ($n Wi-Fi readings)")
                }
            } else {
                val got = app.tools.recordAccuracy(mk, kind.seconds)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (got.isEmpty()) {
                    scope.snack(snackHost, "No position yet at ${mk.name}. Wait a few seconds and try again.")
                } else {
                    val err = got.map { it.errorM }.sorted()[got.size / 2]
                    val aisleOk = got.count { it.aisleCorrect } * 2 >= got.size
                    lastPos = mk.pos; chosen = null
                    scope.snack(snackHost, "${mk.name}: off by ${m(err)}, " + if (aisleOk) "right aisle" else "wrong aisle")
                }
            }
        }
    }

    OnScan { id ->
        val mk = site.marker(id)
        when {
            mk == null -> scope.snack(snackHost, "Code \"$id\" is not on the map", "Add it") { nav.push(Screen.MarkerEdit(code = id)) }
            busy != null -> scope.snack(snackHost, "Wait for this recording to finish")
            !running -> scope.snack(snackHost, "Turn on positioning first")
            else -> record(mk)
        }
    }

    AppScaffold(
        title = kind.title,
        back = true,
        actions = {
            if (kind == WalkKind.SURVEY) {
                IconButton(onClick = { nav.pop(); nav.push(Screen.QuickRecord) }) { Icon(Icons.Filled.AddLocationAlt, "Record anywhere") }
            }
            IconButton(onClick = { scanner.camera("Scan the code at your spot") }) { Icon(Icons.Filled.QrCodeScanner, "Scan code") }
        },
    ) { pad ->
        if (site.markers.isEmpty()) {
            EmptyState(
                Icons.Filled.Place, "No markers yet",
                "Markers are the spots you walk to. Add them first.",
                Modifier.padding(pad),
            ) {
                if (kind == WalkKind.SURVEY) {
                    Button(onClick = { nav.pop(); nav.push(Screen.QuickRecord) }) { Text("Record Wi-Fi anywhere") }
                    TextButton(onClick = { nav.pop(); nav.push(Screen.Markers) }) { Text("Add markers first") }
                } else {
                    Button(onClick = { nav.pop(); nav.push(Screen.Markers) }) { Text("Add markers") }
                }
            }
        } else Column(Modifier.padding(pad).fillMaxSize()) {
            val total = site.markers.size
            LinearProgressIndicator(progress = { done.size / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = CardGap,
            ) {
                Text(
                    "${done.size} of $total spots done",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!running) {
                    WarningCard("Positioning is off", "The app needs it to read Wi-Fi.", "Turn on") { host.startPositioning() }
                } else if (kind == WalkKind.SURVEY && host.scanThrottleOn() == true) {
                    WarningCard(
                        "Android is slowing Wi-Fi scans",
                        "Recordings will be weak. Turn off \"Wi-Fi scan throttling\" in Developer options.",
                        "Fix this",
                    ) { host.openDeveloperOptions() }
                }
                if (kind == WalkKind.SURVEY) LookAlikeWarning(site)
                FloorMap(
                    site,
                    highlight = target?.id,
                    done = done,
                    onMarkerTap = { if (busy == null) chosen = it.id },
                )
                Text(
                    "Green = done. Tap any marker to go there instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val b = busy
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                when {
                    b != null -> RecordingPanel(b, site.marker(b.markerId)?.name ?: b.markerId, onCancel = { job?.cancel() })
                    target == null -> AllDoneCard(kind)
                    else -> NextSpotCard(
                        kind, target, again = target.id in done, enabled = running,
                        onRecord = { record(target) },
                        onSkip = { skipped = skipped + target.id; chosen = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun NextSpotCard(kind: WalkKind, target: Marker, again: Boolean, enabled: Boolean, onRecord: () -> Unit, onSkip: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Go to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(target.name, style = MaterialTheme.typography.headlineMedium)
            if (target.label.isNotBlank()) {
                Text("Code: ${target.id}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                (if (again) "Already done. Recording again replaces it. " else "") +
                    "Stand on the spot, then tap Record. It takes ${kind.seconds} seconds." +
                        if (kind == WalkKind.SURVEY) " Turn around slowly, once, while it records." else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRecord, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("I am here, record")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text("Skip this spot") }
            }
        }
    }
}

@Composable
private fun AllDoneCard(kind: WalkKind) {
    val nav = LocalNav.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = GoodGreen)
                Spacer(Modifier.width(8.dp))
                Text("All spots done", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                if (kind == WalkKind.SURVEY) "Wi-Fi is learned. Next, check how accurate it is."
                else "Your results are ready.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { if (kind == WalkKind.SURVEY) nav.goTab(Tab.TEST) else nav.pop() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (kind == WalkKind.SURVEY) "Go to Test" else "See results") }
        }
    }
}
