package com.swarmamr.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.app.BuildConfig
import com.swarmamr.app.swarm.UiCue

private class EditField(
    val title: String,
    val value: String,
    val help: String,
    val number: Boolean = false,
    val apply: (String) -> Unit,
)

/** Settings as a grouped list. Each text setting opens a small dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val host = LocalHost.current
    val snackHost = LocalSnack.current
    val s by app.settings.state.collectAsStateWithLifecycle()
    val wifi by app.runtime.wifi.collectAsStateWithLifecycle()
    val running by app.runtime.running.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<EditField?>(null) }
    var practise by remember { mutableStateOf(false) }

    /** Wi-Fi settings take effect when positioning restarts. */
    fun restartIfRunning() {
        if (running) {
            app.runtime.stop(); app.runtime.start()
            app.scope.snack(snackHost, "Positioning restarted with the new setting")
        }
    }

    AppScaffold(title = "Settings", back = true) { pad ->
        LazyColumn(Modifier, contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            item { GroupHeader("Traffic server") }
            item {
                SettingRow("Server address", s.backendUrl) {
                    edit = EditField("Server address", s.backendUrl, "The computer that runs the backend, for example http://192.168.1.10:8000") { v ->
                        app.settings.update { it.copy(backendUrl = v) }
                    }
                }
            }
            item {
                SettingRow("Message broker (MQTT)", s.brokerUrl) {
                    edit = EditField("Message broker", s.brokerUrl, "Usually the same computer, for example tcp://192.168.1.10:1883") { v ->
                        app.settings.update { it.copy(brokerUrl = v) }
                    }
                }
            }
            item {
                SettingRow("Site id", s.siteId) {
                    edit = EditField("Site id", s.siteId, "The same id as on the server. Default: demo") { v ->
                        app.settings.update { it.copy(siteId = v.ifBlank { "demo" }) }
                    }
                }
            }
            item {
                SettingRow("This phone's name", s.deviceId) {
                    edit = EditField("This phone's name", s.deviceId, "Shown on the server dashboard") { v ->
                        app.settings.update { it.copy(deviceId = v.ifBlank { it.deviceId }) }
                    }
                }
            }

            item { GroupHeader("Wi-Fi") }
            item {
                SwitchRow(
                    "Use Wi-Fi RTT",
                    if (wifi.rttSupported) "Most accurate. Needs access points that support RTT." else "This phone does not support RTT.",
                    s.useRtt,
                ) { on -> app.settings.update { it.copy(useRtt = on) }; restartIfRunning() }
            }
            item {
                SettingRow("Scan Wi-Fi every", "${s.scanIntervalS} seconds") {
                    edit = EditField("Scan Wi-Fi every (seconds)", "${s.scanIntervalS}", "2 to 60. Lower is faster but uses more battery.", number = true) { v ->
                        v.toIntOrNull()?.let { n -> app.settings.update { it.copy(scanIntervalS = n.coerceIn(2, 60)) }; restartIfRunning() }
                    }
                }
            }
            item {
                SettingRow(
                    "Wi-Fi scan throttling",
                    when (host.scanThrottleOn()) {
                        true -> "On. Android slows scans. Tap to turn it off in Developer options."
                        false -> "Off. Good."
                        null -> "This phone cannot tell. Tap to check in Developer options."
                    },
                ) { host.openDeveloperOptions() }
            }

            item { GroupHeader("Alerts") }
            item { SwitchRow("Sound", "A chime for each alert", s.soundOn) { on -> app.settings.update { it.copy(soundOn = on) } } }
            item { SwitchRow("Vibration", "A different pattern for each alert", s.vibrationOn) { on -> app.settings.update { it.copy(vibrationOn = on) } } }
            item { SettingRow("Practise the alerts", "Learn how each one feels") { practise = true } }

            item { GroupHeader("Help") }
            item { SettingRow("Show the welcome screens again", "") { app.settings.update { it.copy(onboardingDone = false) } } }
            item { SettingRow("App permissions", "Open Android settings for this app") { host.openAppSettings() } }
            item { InfoRow("Version", BuildConfig.VERSION_NAME) }
        }
    }

    edit?.let { f ->
        var text by remember(f) { mutableStateOf(f.value) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(f.title) },
            text = {
                OutlinedTextField(
                    text, { text = it },
                    supportingText = { Text(f.help) },
                    singleLine = true,
                    keyboardOptions = if (f.number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { f.apply(text.trim()); edit = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } },
        )
    }
    if (practise) {
        AlertDialog(
            onDismissRequest = { practise = false },
            title = { Text("Practise the alerts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tap each one. Learn to tell them apart without looking.", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(UiCue.WARNING, UiCue.HOLD, UiCue.GO, UiCue.REROUTE, UiCue.OFFLINE).forEach { c ->
                            OutlinedButton(onClick = { app.runtime.practiceCue(c) }) { Text(cueWord(c)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { practise = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    val sub: (@Composable () -> Unit)? = if (value.isEmpty()) null else { { Text(value) } }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(title: String, help: String, on: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(help) },
        trailingContent = { Switch(checked = on, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!on) },
    )
}
