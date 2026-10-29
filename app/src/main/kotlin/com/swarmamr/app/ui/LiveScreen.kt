package com.swarmamr.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.app.SwarmApp
import com.swarmamr.app.swarm.LinkState
import com.swarmamr.app.swarm.UiCue
import kotlinx.coroutines.delay

@Composable
fun LiveScreen(app: SwarmApp, onStart: () -> Unit, onStop: () -> Unit, onScanQr: () -> Unit) {
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
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (swarm) {
            val countdown = if (cue.cue == UiCue.HOLD && cue.holdUntilMs > now) " ${(cue.holdUntilMs - now + 999) / 1000} s" else ""
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cueColor(cue.cue)).padding(16.dp),
            ) {
                Column {
                    Text(
                        if (cue.cue == UiCue.HOLD) "Wait$countdown" else cue.text.ifBlank { cueWord(cue.cue) },
                        color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    )
                    val picking = if (pick.picking) "  · picking" else ""
                    val pickLine = if (pick.total > 0) "Next pick: ${pick.label}  (${pick.done}/${pick.total})$picking" else ""
                    Text(pickLine, color = Color.White, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.padding(4.dp))
        }

        Section("Position") {
            val e = est
            if (!running) {
                Text("Positioning is off. Press Start.")
            } else if (e == null) {
                Text("Waiting for Wi-Fi data… (scan a QR marker to start at a known spot)")
            } else {
                KeyValue("Area", e.snapped.area)
                KeyValue("Source", e.source.name.replace('_', ' ') + if (e.apsUsed > 0) " · ${e.apsUsed} APs" else "")
                KeyValue("Uncertainty", "± " + m(e.sigmaM))
                KeyValue("x, y", "${m(e.snapped.pos.x)}, ${m(e.snapped.pos.y)}")
                KeyValue("Age of data", "${(now - e.timeMs) / 1000} s")
            }
            KeyValue("Wi-Fi RTT on this phone", if (wifi.rttSupported) (if (wifi.rttAvailable) "yes" else "yes, but off") else "no (signal strength only)")
            KeyValue("RTT access points in range", wifi.seen.count { it.rttResponder }.toString())
            KeyValue("Moving", if (wifi.stationary) "standing still" else "walking")
        }

        FloorMap(site, est, pick.target, Modifier.padding(vertical = 8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (running) OutlinedButton(onClick = onStop) { Text("Stop") } else Button(onClick = onStart) { Text("Start") }
            OutlinedButton(onClick = onScanQr) { Text("Scan QR marker") }
        }

        Section("Swarm demo") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Get traffic cues", Modifier.weight(1f))
                Switch(checked = swarm, enabled = running, onCheckedChange = { rt.setSwarm(it) })
            }
            if (swarm) {
                KeyValue("Server link", when (link) {
                    LinkState.CONNECTED -> "connected"
                    LinkState.CONNECTING -> "connecting…"
                    LinkState.RECONNECTING -> "reconnecting…"
                    LinkState.FAILED -> "failed (check broker address in Settings)"
                    LinkState.OFF -> "off"
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { rt.nextPick() }) { Text("Pick done, next") }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    "Walk to the pink circle. Stand there 6 s to finish the pick. Follow the banner: " +
                        "Slow, Wait, Go, Reroute.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (!running) {
                Text("Start positioning first.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
