package com.swarmamr.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** First run: what the app does, the location permission, and the Wi-Fi throttling check. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val host = LocalHost.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val granted = host.permissionsGranted
    LaunchedEffect(granted) { if (granted && page == 1) page = 2 }

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(
                            if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    0 -> {
                        PageTitle(Icons.Filled.Explore, "Welcome")
                        Text(
                            "This app shows where you are inside a building using Wi-Fi. " +
                                "It can also tell people when to wait, so two people do not meet in a narrow aisle.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        StepLine(1, "Set up", "Draw the floor and add markers, like desk QR codes.")
                        StepLine(2, "Test", "Walk to the markers to see how accurate it is.")
                        StepLine(3, "Live", "See where you are and get traffic alerts.")
                    }
                    1 -> {
                        PageTitle(Icons.Filled.LocationOn, "Allow location")
                        Text(
                            "Android only shares Wi-Fi details with apps that have location access. " +
                                "The app does not use GPS. It sends your position only to the traffic server you choose.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (granted) Done("Location is allowed")
                    }
                    else -> {
                        PageTitle(Icons.Filled.Wifi, "Faster Wi-Fi scans")
                        Text(
                            "Android allows only 4 Wi-Fi scans every 2 minutes. That is too slow to follow you. " +
                                "Turn off \"Wi-Fi scan throttling\" in Developer options.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        when (host.scanThrottleOn()) {
                            false -> Done("Throttling is already off")
                            else -> {
                                Text(
                                    "No Developer options? Open Settings > About phone and tap \"Build number\" 7 times. " +
                                        "Then find Developer options under Settings > System.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { host.openDeveloperOptions() }) { Text("Open Developer options") }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) TextButton(onClick = { page-- }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                when (page) {
                    0 -> Button(onClick = { page = 1 }) { Text("Get started") }
                    1 -> if (granted) Button(onClick = { page = 2 }) { Text("Next") } else {
                        TextButton(onClick = { page = 2 }) { Text("Not now") }
                        Button(onClick = { host.requestPermissions() }) { Text("Allow") }
                    }
                    else -> Button(onClick = onDone) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun PageTitle(icon: ImageVector, title: String) {
    Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun StepLine(n: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("$n", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Done(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, null, tint = GoodGreen)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
