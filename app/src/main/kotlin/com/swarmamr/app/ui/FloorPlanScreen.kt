package com.swarmamr.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.core.GridSpec
import kotlin.math.roundToInt

/** Starting points for the two kinds of place. */
private val WAREHOUSE = GridSpec(aislePitchM = 3.0, cellM = 1.2, nAisles = 8, aisleLenCells = 12, narrowAisles = listOf(1, 2, 5, 6))
private val OFFICE = GridSpec(aislePitchM = 3.0, cellM = 1.0, nAisles = 4, aisleLenCells = 10, narrowAisles = emptyList())

/** Floor plan editor: the type of place, then a few steppers, with a live preview. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FloorPlanScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val snackHost = LocalSnack.current
    val site by app.sites.site.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(site.name) }
    var g by remember { mutableStateOf(site.grid) }
    // 0 warehouse, 1 office with walkways, 2 open floor
    var kind by remember { mutableIntStateOf(if (site.grid.openFloor) 2 else if (site.grid.narrowAisles.isEmpty()) 1 else 0) }
    val office = kind == 1
    val open = kind == 2
    var advanced by remember { mutableStateOf(false) }
    var askMarkers by remember { mutableStateOf(false) }
    var cellText by remember { mutableStateOf(num(g.cellM, 2)) }
    var oxText by remember { mutableStateOf(num(g.originX, 1)) }
    var oyText by remember { mutableStateOf(num(g.originY, 1)) }
    val word = if (office) "walkway" else "aisle"
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var askRemove by remember { mutableStateOf(false) }
    val img = site.image
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val got = PlanImages.import(ctx, uri)
            importing = false
            if (got == null) scope.snack(snackHost, "Could not open this picture. Try a PNG or JPG file.")
            else nav.push(Screen.PlanScale(got.first, got.second, got.third))
        }
    }

    fun lengthM() = g.aisleLenCells * g.cellM
    fun setLength(len: Double) { g = g.copy(aisleLenCells = (len / g.cellM).roundToInt().coerceIn(2, 300)) }

    fun save() {
        app.sites.setName(name)
        if (img == null) app.sites.setGrid(g)
        app.settings.update { it.copy(floorPlanSaved = true) }
        if ((open || img != null) && site.markers.isEmpty()) { nav.pop(); nav.push(Screen.QuickRecord) }
        else if (site.markers.isEmpty()) askMarkers = true
        else { app.scope.snack(snackHost, "Floor plan saved"); nav.pop() }
    }

    AppScaffold(
        title = "Floor plan",
        back = true,
        actions = { TextButton(onClick = { save() }) { Text("Save") } },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                name, { name = it },
                label = { Text("Place name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Floor plan picture", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        if (img == null) "Have a drawing of the floor? Use it as the map. Then you just tap where you stand to record. " +
                            "You need one length you know, like a long table."
                        else "In use. The floor is about ${m(img.widthM, 0)} wide and ${m(img.heightM, 0)} deep.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (img != null) {
                            TextButton(onClick = { askRemove = true }) { Text("Remove") }
                            TextButton(onClick = { nav.push(Screen.PlanScale(img.file, img.widthPx, img.heightPx)) }) { Text("Set scale") }
                        }
                        FilledTonalButton(onClick = { picker.launch("image/*") }, enabled = !importing) {
                            Text(if (img == null) "Choose picture" else "Change")
                        }
                    }
                }
            }

            if (img == null) {
            Text("Type of place", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = kind == 0,
                    onClick = { kind = 0; g = WAREHOUSE.copy(originX = g.originX, originY = g.originY); cellText = num(g.cellM, 2) },
                    shape = SegmentedButtonDefaults.itemShape(0, 3),
                ) { Text("Warehouse") }
                SegmentedButton(
                    selected = kind == 1,
                    onClick = { kind = 1; g = OFFICE.copy(originX = g.originX, originY = g.originY); cellText = num(g.cellM, 2) },
                    shape = SegmentedButtonDefaults.itemShape(1, 3),
                ) { Text("Walkways") }
                SegmentedButton(
                    selected = kind == 2,
                    onClick = { kind = 2; g = GridSpec.open(20, 12, g.originX, g.originY); cellText = num(g.cellM, 2) },
                    shape = SegmentedButtonDefaults.itemShape(2, 3),
                ) { Text("Open floor") }
            }
            Text(
                when (kind) {
                    0 -> "Pick aisles run between a front and a back cross-aisle."
                    1 -> "An office or home with clear walkways between desk rows. Draw each walkway."
                    else -> "No fixed paths, like an open office or a home. Just give the size. " +
                        "Then record Wi-Fi anywhere: tap the map or scan a code."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (open) {
                Stepper("Width", m(g.widthM, 0), onMinus = {
                    g = g.copy(nAisles = (g.nAisles - 1).coerceAtLeast(2))
                }, onPlus = { g = g.copy(nAisles = (g.nAisles + 1).coerceAtMost(400)) })
                Stepper("Depth", m(g.depthM, 0), onMinus = {
                    g = g.copy(aisleLenCells = (g.aisleLenCells - 1).coerceAtLeast(1))
                }, onPlus = { g = g.copy(aisleLenCells = (g.aisleLenCells + 1).coerceAtMost(400)) })
                Text(
                    "Measure the rectangle that holds the whole floor. It does not need to be exact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
            Stepper("Number of ${word}s", "${g.nAisles}", onMinus = {
                val k = (g.nAisles - 1).coerceAtLeast(1)
                g = g.copy(nAisles = k, narrowAisles = g.narrowAisles.filter { it < k })
            }, onPlus = { g = g.copy(nAisles = (g.nAisles + 1).coerceAtMost(40)) })
            Stepper("Length of each $word", m(lengthM()), onMinus = { setLength(lengthM() - 1) }, onPlus = { setLength(lengthM() + 1) })
            Stepper("Space between ${word}s", m(g.aislePitchM), onMinus = {
                g = g.copy(aislePitchM = (g.aislePitchM - 0.5).coerceAtLeast(1.0))
            }, onPlus = { g = g.copy(aislePitchM = (g.aislePitchM + 0.5).coerceAtMost(12.0)) })

            Column {
                Text("Narrow ${word}s", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Only one person fits at a time. Traffic cues stop two people meeting in them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (a in 0 until g.nAisles) {
                        val on = a in g.narrowAisles
                        FilterChip(
                            selected = on,
                            onClick = { g = g.copy(narrowAisles = if (on) g.narrowAisles - a else (g.narrowAisles + a).sorted()) },
                            label = { Text("A$a") },
                        )
                    }
                }
            }
            }

            }

            Text("Preview", style = MaterialTheme.typography.titleSmall)
            FloorMap(if (img == null) site.copy(grid = g) else site)
            Text(
                if (img != null) "The picture sets the size. Remove it to draw aisles or walkways instead."
                else "About ${m(g.widthM, 0)} wide and ${m(g.depthM, 0)} deep. " +
                    (if (open) "" else "Orange ${word}s are narrow. ") + "Pinch to zoom.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(onClick = { advanced = !advanced }) {
                Text("More settings")
                Icon(if (advanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (advanced && img == null) {
                val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                OutlinedTextField(
                    cellText, { t -> cellText = t; parseNum(t)?.takeIf { it in 0.3..5.0 }?.let { c -> val len = lengthM(); g = g.copy(cellM = c); setLength(len) } },
                    label = { Text("Grid step along a $word (m)") },
                    supportingText = { Text("Positions snap to this step. 1 to 1.5 m works well.") },
                    singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        oxText, { t -> oxText = t; parseNum(t)?.let { g = g.copy(originX = it) } },
                        label = { Text("Start x (m)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        oyText, { t -> oyText = t; parseNum(t)?.let { g = g.copy(originY = it) } },
                        label = { Text("Start y (m)") }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "x and y are metres from a corner of the building. Only change them if your access points or markers use other numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (askRemove) {
        AlertDialog(
            onDismissRequest = { askRemove = false },
            title = { Text("Remove the picture?") },
            text = { Text("The map goes back to a plain rectangle. Your recordings stay.") },
            confirmButton = { TextButton(onClick = { askRemove = false; app.sites.setImage(null) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { askRemove = false }) { Text("Cancel") } },
        )
    }

    if (askMarkers) {
        AlertDialog(
            onDismissRequest = { askMarkers = false; nav.pop() },
            title = { Text("Make markers for you?") },
            text = {
                Text(
                    "The app can put a marker every few metres along each $word, named with your own pattern (like L0432). " +
                        "If your desks already have QR codes, choose Not now and scan them on the Markers screen.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askMarkers = false
                    nav.pop()
                    nav.push(Screen.MarkerNames)
                }) { Text("Choose names") }
            },
            dismissButton = { TextButton(onClick = { askMarkers = false; nav.pop() }) { Text("Not now") } },
        )
    }
}
