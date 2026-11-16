package com.swarmamr.app.ui

import androidx.compose.foundation.layout.Arrangement
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
    var office by remember { mutableStateOf(site.grid.narrowAisles.isEmpty()) }
    var advanced by remember { mutableStateOf(false) }
    var askMarkers by remember { mutableStateOf(false) }
    var cellText by remember { mutableStateOf(num(g.cellM, 2)) }
    var oxText by remember { mutableStateOf(num(g.originX, 1)) }
    var oyText by remember { mutableStateOf(num(g.originY, 1)) }
    val word = if (office) "walkway" else "aisle"

    fun lengthM() = g.aisleLenCells * g.cellM
    fun setLength(len: Double) { g = g.copy(aisleLenCells = (len / g.cellM).roundToInt().coerceIn(2, 300)) }

    fun save() {
        app.sites.setName(name)
        app.sites.setGrid(g)
        app.settings.update { it.copy(floorPlanSaved = true) }
        if (site.markers.isEmpty()) askMarkers = true
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

            Text("Type of place", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !office,
                    onClick = { office = false; g = WAREHOUSE.copy(originX = g.originX, originY = g.originY); cellText = num(g.cellM, 2) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Warehouse") }
                SegmentedButton(
                    selected = office,
                    onClick = { office = true; g = OFFICE.copy(originX = g.originX, originY = g.originY); cellText = num(g.cellM, 2) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Office or home") }
            }
            Text(
                if (office) "Draw each walkway between desk rows. People walk along the walkways and along the two paths that join them at the ends."
                else "Pick aisles run between a front and a back cross-aisle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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

            Text("Preview", style = MaterialTheme.typography.titleSmall)
            FloorMap(site.copy(grid = g))
            Text(
                "About ${m(g.nAisles * g.aislePitchM, 0)} wide and ${m(g.topY() - g.bottomY(), 0)} deep. " +
                    "Orange ${word}s are narrow. Pinch to zoom.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(onClick = { advanced = !advanced }) {
                Text("More settings")
                Icon(if (advanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (advanced) {
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
