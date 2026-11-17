package com.swarmamr.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.core.MapMatcher
import com.swarmamr.core.Marker
import com.swarmamr.core.Vec2

/** All markers on a map and in a list. Scanning a code opens it, or starts a new marker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkersScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scanner = LocalScan.current
    val snackHost = LocalSnack.current
    val site by app.sites.site.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val recorded = remember(site) { site.markers.filter { site.surveyed(it) }.map { it.id }.toSet() }

    OnScan { id ->
        val mk = site.marker(id)
        nav.push(if (mk != null) Screen.MarkerEdit(id = mk.id) else Screen.MarkerEdit(code = id))
    }

    AppScaffold(
        title = "Markers",
        back = true,
        actions = {
            OverflowMenu(
                listOf(
                    MenuAction("Make markers on every aisle") { nav.push(Screen.MarkerNames) },
                    MenuAction("Delete all markers") { confirmDelete = true },
                ),
            )
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { sheet = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add marker") },
            )
        },
    ) { pad ->
        if (site.markers.isEmpty()) {
            EmptyState(
                Icons.Filled.QrCode2, "No markers yet",
                "A marker is a spot you can stand on, like a desk. If it has a QR code, scan it. If not, give it a name.",
                Modifier.padding(pad),
            ) {
                Button(onClick = { sheet = true }) { Text("Add marker") }
                TextButton(onClick = { nav.push(Screen.MarkerNames) }) { Text("Or make them on every aisle, named your way") }
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        FloorMap(site, done = recorded, onMarkerTap = { nav.push(Screen.MarkerEdit(id = it.id)) })
                        Text(
                            "Tap a marker to change it. Green means Wi-Fi is recorded there.",
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item { GroupHeader("${site.markers.size} markers") }
                items(site.markers, key = { it.id }) { mk ->
                    val rec = mk.id in recorded
                    ListItem(
                        headlineContent = { Text(mk.name) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    if (mk.label.isNotBlank()) "Code ${mk.id}" else null,
                                    if (rec) "Wi-Fi recorded" else "Wi-Fi not recorded yet",
                                ).joinToString(" · "),
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (rec) Icons.Filled.CheckCircle else Icons.Filled.Place, null,
                                tint = if (rec) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable { nav.push(Screen.MarkerEdit(id = mk.id)) },
                    )
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Text("Add a marker", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            ListItem(
                headlineContent = { Text("Scan its QR code") },
                supportingContent = { Text("Uses the camera. On a Zebra device you can also press the scan button.") },
                leadingContent = { Icon(Icons.Filled.QrCodeScanner, null) },
                modifier = Modifier.clickable { sheet = false; scanner.camera("Scan the marker's QR code") },
            )
            ListItem(
                headlineContent = { Text("No QR code? Type a name") },
                supportingContent = { Text("For example Desk 12. You can add the QR code later.") },
                leadingContent = { Icon(Icons.Filled.Keyboard, null) },
                modifier = Modifier.clickable { sheet = false; nav.push(Screen.MarkerEdit()) },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all markers?") },
            text = { Text("Their Wi-Fi recordings are deleted too. Test results stay.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; app.sites.clearMarkers() }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Add or change one marker: its code, a friendly name and where it is. */
@Composable
fun MarkerEditScreen(id: String?, code: String?) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scanner = LocalScan.current
    val snackHost = LocalSnack.current
    val site by app.sites.site.collectAsStateWithLifecycle()
    val est by app.runtime.estimate.collectAsStateWithLifecycle()
    val existing: Marker? = remember(id) { id?.let { site.marker(it) } }

    var codeText by remember { mutableStateOf(existing?.id ?: code ?: "") }
    var nameText by remember { mutableStateOf(existing?.label ?: "") }
    var pos by remember { mutableStateOf(existing?.pos) }
    var snap by remember { mutableStateOf(true) }
    var xText by remember { mutableStateOf(existing?.let { num(it.x, 2) } ?: "") }
    var yText by remember { mutableStateOf(existing?.let { num(it.y, 2) } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val matcher = remember(site.grid) { MapMatcher(site.grid) }

    fun place(p: Vec2) {
        val q = if (snap) matcher.snapRaw(p).pos else p
        pos = q; xText = num(q.x, 2); yText = num(q.y, 2)
    }

    val trimmed = codeText.trim()
    val clash = trimmed.isNotEmpty() && !trimmed.equals(existing?.id, ignoreCase = true) &&
        site.markers.any { it.id.equals(trimmed, ignoreCase = true) }
    val canSave = trimmed.isNotEmpty() && pos != null && !clash

    fun save() {
        val p = pos ?: return
        val mk = Marker(trimmed, p.x, p.y, existing?.note ?: "", nameText.trim())
        app.sites.upsertMarker(mk, oldId = existing?.id)
        app.scope.snack(snackHost, "Saved ${mk.name}")
        nav.pop()
    }

    OnScan { codeText = it }

    AppScaffold(
        title = if (existing == null) "New marker" else "Edit marker",
        back = true,
        actions = { TextButton(onClick = { save() }, enabled = canSave) { Text("Save") } },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                codeText, { codeText = it },
                label = { Text("Code") },
                supportingText = {
                    Text(if (clash) "Another marker already has this code" else "The text in its QR code, like a desk number. No code? Type any name.")
                },
                isError = clash,
                trailingIcon = {
                    IconButton(onClick = { scanner.camera("Scan the marker's QR code") }) { Icon(Icons.Filled.QrCodeScanner, "Scan code") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                nameText, { nameText = it },
                label = { Text("Name to show (optional)") },
                supportingText = { Text("For example: Desk 12, window side") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Where is it?", style = MaterialTheme.typography.titleMedium)
            Text(
                if (pos == null) "Tap the map where this marker is." else "Tap the map again to move it.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pos == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FloorMap(site, me = est, pin = pos, onTap = { place(it) })
            if (!site.grid.openFloor) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Put it on the nearest walkway", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Recommended. The app always shows people on a walkway.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(snap, { on -> snap = on; pos?.let { place(it) } })
            }
            OutlinedButton(onClick = { est?.let { place(it.snapped.pos) } }, enabled = est != null) {
                Icon(Icons.Filled.MyLocation, null)
                Spacer(Modifier.width(8.dp))
                Text("Use my current position")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                OutlinedTextField(
                    xText, { t -> xText = t; val x = parseNum(t); val y = pos?.y ?: parseNum(yText); if (x != null && y != null) pos = Vec2(x, y) },
                    label = { Text("x (m)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    yText, { t -> yText = t; val y = parseNum(t); val x = pos?.x ?: parseNum(xText); if (x != null && y != null) pos = Vec2(x, y) },
                    label = { Text("y (m)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f),
                )
            }
            if (existing != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete marker")
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("Its Wi-Fi recording is deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    app.sites.removeMarker(existing.id)
                    app.scope.snack(snackHost, "Deleted ${existing.name}")
                    nav.pop()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
