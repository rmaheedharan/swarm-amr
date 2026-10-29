package com.swarmamr.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.app.BuildConfig
import com.swarmamr.app.SwarmApp
import com.swarmamr.app.swarm.UiCue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: SwarmApp) {
    val ctx = LocalContext.current
    val s by app.settings.state.collectAsStateWithLifecycle()
    val wifi by app.runtime.wifi.collectAsStateWithLifecycle()
    var deviceId by remember(s.deviceId) { mutableStateOf(s.deviceId) }
    var broker by remember(s.brokerUrl) { mutableStateOf(s.brokerUrl) }
    var backend by remember(s.backendUrl) { mutableStateOf(s.backendUrl) }
    var siteId by remember(s.siteId) { mutableStateOf(s.siteId) }
    var scanS by remember(s.scanIntervalS) { mutableStateOf(s.scanIntervalS.toString()) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        Section("Server") {
            Text("Use the address of the computer that runs the backend (docker compose).",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(deviceId, { deviceId = it; saved = false }, label = { Text("This device name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(broker, { broker = it; saved = false }, label = { Text("MQTT broker, e.g. tcp://192.168.1.10:1883") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(backend, { backend = it; saved = false }, label = { Text("Backend, e.g. http://192.168.1.10:8000") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(siteId, { siteId = it; saved = false }, label = { Text("Site id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(scanS, { scanS = it; saved = false }, label = { Text("Wi-Fi scan every (s)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                app.settings.update {
                    it.copy(
                        deviceId = deviceId.trim().ifBlank { it.deviceId },
                        brokerUrl = broker, backendUrl = backend, siteId = siteId.ifBlank { "demo" },
                        scanIntervalS = scanS.toIntOrNull()?.coerceIn(2, 60) ?: it.scanIntervalS,
                    )
                }
                saved = true
            }) { Text(if (saved) "Saved (restart positioning to apply)" else "Save") }
        }

        Section("Positioning") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use Wi-Fi RTT when available", Modifier.weight(1f))
                Switch(s.useRtt, { v -> app.settings.update { it.copy(useRtt = v) } })
            }
            KeyValue("Wi-Fi RTT on this phone", if (wifi.rttSupported) "supported" else "not supported")
            KeyValue("Wi-Fi scans asked / refused", "${wifi.scansRequested} / ${wifi.scansRefused}")
            Text(
                "Android allows only 4 Wi-Fi scans every 2 minutes. For fingerprint positioning, turn this off: " +
                    "Settings > Developer options > Wi-Fi scan throttling. Location must be on.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Open Developer options") }
        }

        Section("Cues") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vibration", Modifier.weight(1f))
                Switch(s.vibrationOn, { v -> app.settings.update { it.copy(vibrationOn = v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sound", Modifier.weight(1f))
                Switch(s.soundOn, { v -> app.settings.update { it.copy(soundOn = v) } })
            }
            Text("Practice: feel each pattern until you can tell them apart without looking.",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(UiCue.WARNING, UiCue.HOLD, UiCue.GO, UiCue.REROUTE, UiCue.OFFLINE).forEach { c ->
                    OutlinedButton(onClick = { app.runtime.practiceCue(c) }) { Text(cueWord(c)) }
                }
            }
        }

        Section("About") {
            KeyValue("Version", BuildConfig.VERSION_NAME)
            KeyValue("Device name", s.deviceId)
        }
    }
}
