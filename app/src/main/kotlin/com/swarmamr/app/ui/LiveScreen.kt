package com.swarmamr.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.app.PickUi
import com.swarmamr.app.swarm.CueUi
import com.swarmamr.app.swarm.LinkState
import com.swarmamr.app.swarm.UiCue
import com.swarmamr.core.Estimate
import com.swarmamr.core.Site
import kotlinx.coroutines.delay

/** The Live tab: where am I, the map, and the traffic demo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val host = LocalHost.current
    val scanner = LocalScan.current
    val snackHost = LocalSnack.current
    val scope = rememberCoroutineScope()
    val rt = app.runtime
    val running by rt.running.collectAsStateWithLifecycle()
    val est by rt.estimate.collectAsStateWithLifecycle()
    val wifi by rt.wifi.collectAsStateWithLifecycle()
    val site by app.sites.site.collectAsStateWithLifecycle()
    val swarm by rt.swarmOn.collectAsStateWithLifecycle()
    val cue by rt.cues.ui.collectAsStateWithLifecycle()
    val link by rt.linkState.collectAsStateWithLifecycle()
    val pick by rt.pick.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var panel by remember { mutableStateOf(false) }
    val mapState = rememberMapState()
    // Start in follow mode: the map opens centred on the user, like a maps app.
    LaunchedEffect(Unit) { mapState.follow = true }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }

    OnScan { id ->
        when {
            !running -> scope.snack(snackHost, "Turn on positioning first")
            rt.qrCheckpoint(id) -> scope.snack(snackHost, "Position set to ${site.marker(id)?.name ?: id}")
            else -> scope.snack(snackHost, "Code \"$id\" is not on the map", "Add it") { nav.push(Screen.MarkerEdit(code = id)) }
        }
    }

    AppScaffold(
        title = "Live",
        actions = {
            OverflowMenu(
                listOf(
                    if (running) MenuAction("Turn off positioning") { host.stopPositioning() }
                    else MenuAction("Turn on positioning") { host.startPositioning() },
                    MenuAction("Show whole map") { mapState.reset() },
                ),
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            FloorMap(site, me = est, target = pick.target, state = mapState, fill = true)

            // Cards float on top of the map, like a maps app.
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (swarm) CueBanner(cue, pick, now)
                val e = est
                when {
                    !running -> FloatingCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Positioning is off", style = MaterialTheme.typography.titleMedium)
                                Text("Turn it on to see where you are.", style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(onClick = { host.startPositioning() }) { Text("Turn on") }
                        }
                    }
                    e == null -> FloatingCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Finding you…", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (site.fingerprints.isEmpty() && site.accessPoints.isEmpty()) "Wi-Fi is not taught yet. Do Set up first, or scan a code."
                                    else "Walk a few steps, or scan a code to start at a known spot.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    else -> WhereCard(e, site, now)
                }
            }

            // Map buttons on the right, like a maps app.
            Column(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { mapState.zoomBy(1.6f) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { Icon(Icons.Filled.Add, "Zoom in") }
                SmallFloatingActionButton(
                    onClick = { mapState.follow = false; mapState.zoomBy(1 / 1.6f) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { Icon(Icons.Filled.Remove, "Zoom out") }
                FloatingActionButton(
                    onClick = { mapState.follow = !mapState.follow },
                    containerColor = if (mapState.follow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(
                        if (mapState.follow) Icons.Filled.MyLocation else Icons.Filled.LocationSearching,
                        if (mapState.follow) "Stop following me" else "Follow me",
                        tint = if (mapState.follow) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Actions on the left: scan, and the traffic demo panel.
            Column(
                Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { panel = true },
                    icon = { Icon(Icons.Filled.Traffic, null) },
                    text = { Text(if (swarm) "Traffic on" else "Traffic") },
                    containerColor = if (swarm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                if (running) {
                    ExtendedFloatingActionButton(
                        onClick = { scanner.camera("Scan the code where you stand") },
                        icon = { Icon(Icons.Filled.QrCodeScanner, null) },
                        text = { Text("Scan") },
                    )
                }
            }
        }
    }

    if (panel) {
        ModalBottomSheet(onDismissRequest = { panel = false }) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrafficCard(swarm, running, link, pick, onToggle = { rt.setSwarm(it) }, onNext = { rt.nextPick() },
                    onSettings = { panel = false; nav.push(Screen.Settings) })
                Text("Details", style = MaterialTheme.typography.titleSmall)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        est?.let {
                            KeyValue("Method", sourceName(it.source.name) + if (it.apsUsed > 0) " · ${it.apsUsed} APs" else "")
                            KeyValue("x, y", "${m(it.snapped.pos.x)}, ${m(it.snapped.pos.y)}")
                            KeyValue("Speed", "${num(it.speed)} m/s")
                        }
                        KeyValue("Wi-Fi RTT on this phone", if (wifi.rttSupported) (if (wifi.rttAvailable) "yes" else "yes, but off") else "no")
                        KeyValue("RTT access points in range", "${wifi.seen.count { it.rttResponder }}")
                        KeyValue("Access points seen", "${wifi.seen.size}")
                        KeyValue("Wi-Fi scans asked / refused", "${wifi.scansRequested} / ${wifi.scansRefused}")
                        KeyValue("Moving", if (wifi.stationary) "standing still" else "walking")
                    }
                }
            }
        }
    }
}

/** A card that floats over the map. */
@Composable
private fun FloatingCard(content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

fun sourceName(s: String) = when (s) {
    "RTT" -> "Wi-Fi RTT"
    "FINGERPRINT" -> "Wi-Fi recordings"
    "RSSI_CENTROID" -> "Signal strength"
    "QR" -> "QR code"
    else -> s
}

@Composable
private fun WhereCard(e: Estimate, site: Site, now: Long) {
    val near = site.markers.minByOrNull { it.pos.dist(e.snapped.pos) }
    val area = when {
        e.snapped.zone != null -> "area ${e.snapped.zone}"
        e.snapped.aisle != null -> "aisle A${e.snapped.aisle}"
        else -> "a cross path"
    }
    val age = (now - e.timeMs) / 1000
    ElevatedCard(Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("You are", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            val other = e.unsureWith?.let { site.marker(it)?.name ?: it }
            Text(
                when {
                    near != null && other != null && other != near.name -> "Near ${near.name} or $other"
                    near != null && near.pos.dist(e.snapped.pos) < 3.0 -> "Near ${near.name}"
                    else -> "In $area"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (other != null) {
                Text(
                    "Wi-Fi looks alike at these two spots. Stand still a few seconds, or scan a code to be sure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnAmber,
                )
            }
            Text(
                (if (near != null && near.pos.dist(e.snapped.pos) < 3.0) "In $area · " else "") +
                    "within about ${m(e.sigmaM)} · ${sourceName(e.source.name)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (age > 10) {
                Text("Last update $age s ago", style = MaterialTheme.typography.bodySmall, color = WarnAmber)
            }
        }
    }
}

@Composable
private fun CueBanner(cue: CueUi, pick: PickUi, now: Long) {
    val countdown = if (cue.cue == UiCue.HOLD && cue.holdUntilMs > now) " ${(cue.holdUntilMs - now + 999) / 1000} s" else ""
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cueColor(cue.cue)).padding(20.dp),
    ) {
        Text(
            if (cue.cue == UiCue.HOLD) "Wait$countdown" else cueWord(cue.cue),
            color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
        )
        if (cue.text.isNotBlank()) Text(cue.text, color = Color.White, fontSize = 16.sp)
        if (pick.total > 0) {
            Text(
                "Next pick: ${pick.label} (${pick.done} of ${pick.total})" + if (pick.picking) " · picking" else "",
                color = Color.White, fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun TrafficCard(
    swarm: Boolean,
    running: Boolean,
    link: LinkState,
    pick: PickUi,
    onToggle: (Boolean) -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Traffic demo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !running -> "Turn on positioning first."
                            !swarm -> "Get Wait and Go alerts from the traffic server."
                            else -> when (link) {
                                LinkState.CONNECTED -> "Connected to the server"
                                LinkState.CONNECTING -> "Connecting…"
                                LinkState.RECONNECTING -> "Lost the server. Trying again…"
                                LinkState.FAILED -> "Cannot reach the server"
                                LinkState.OFF -> "Off"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (swarm && link == LinkState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = swarm, enabled = running, onCheckedChange = onToggle)
            }
            if (swarm && link == LinkState.FAILED) {
                TextButton(onClick = onSettings) { Text("Check the server address") }
            }
            if (swarm && pick.total > 0) {
                Text(
                    "Walk to the pink circle and stand there 6 s to finish the pick. Follow the alerts.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onNext) { Text("Pick done, next") }
            }
        }
    }
}
