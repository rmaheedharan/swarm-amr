package com.swarmamr.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.core.MarkerNaming

private class Preset(val example: String, val naming: MarkerNaming)

private val PRESETS = listOf(
    Preset("L0432", MarkerNaming("L{AA}{SS}", firstAisle = 1, firstSpot = 1)),
    Preset("A3-07", MarkerNaming("A{A}-{RR}", firstAisle = 0, firstSpot = 1)),
    Preset("0001", MarkerNaming("{NNNN}", firstAisle = 1, firstSpot = 1)),
)

/** Make a marker every few metres on every aisle, named by the user's own pattern. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkerNamesScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val snackHost = LocalSnack.current
    val site by app.sites.site.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(site.naming) }
    var confirm by remember { mutableStateOf(false) }
    val g = site.grid
    val preview = remember(naming, g) { naming.markers(g) }
    val unique = remember(naming, g) { naming.isUnique(g) }
    val ok = naming.pattern.isNotBlank() && unique && preview.isNotEmpty()

    fun make() {
        app.sites.makeMarkers(naming)
        app.scope.snack(snackHost, "Made ${preview.size} markers")
        nav.pop()
    }

    AppScaffold(
        title = "Name markers",
        back = true,
        actions = { TextButton(onClick = { if (site.markers.isEmpty()) make() else confirm = true }, enabled = ok) { Text("Make") } },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "The app puts a marker every few metres on every aisle and names it with your pattern, " +
                    "so the names match the codes on your floor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Start from an example", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { p ->
                    FilterChip(
                        selected = naming.pattern == p.naming.pattern,
                        onClick = { naming = p.naming.copy(everyCells = naming.everyCells) },
                        label = { Text(p.example) },
                    )
                }
            }
            OutlinedTextField(
                naming.pattern, { naming = naming.copy(pattern = it) },
                label = { Text("Pattern") },
                supportingText = {
                    Text(
                        if (!unique) "Two markers would get the same name. Add {S} or {N}."
                        else "{AA} = aisle number, {SS} = spot number in the aisle, {NNNN} = running number. " +
                            "More letters = more leading zeros. Other text stays as it is.",
                    )
                },
                isError = !unique,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper("First aisle number", "${naming.firstAisle}", onMinus = {
                naming = naming.copy(firstAisle = (naming.firstAisle - 1).coerceAtLeast(0))
            }, onPlus = { naming = naming.copy(firstAisle = naming.firstAisle + 1) })
            Stepper("First spot number", "${naming.firstSpot}", onMinus = {
                naming = naming.copy(firstSpot = (naming.firstSpot - 1).coerceAtLeast(0))
            }, onPlus = { naming = naming.copy(firstSpot = naming.firstSpot + 1) })
            Stepper("One marker every", m(naming.everyCells * g.cellM), onMinus = {
                naming = naming.copy(everyCells = (naming.everyCells - 1).coerceAtLeast(1))
            }, onPlus = { naming = naming.copy(everyCells = (naming.everyCells + 1).coerceAtMost(20)) })

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Preview: ${preview.size} markers", style = MaterialTheme.typography.titleSmall)
                    if (preview.isNotEmpty()) {
                        Text("First aisle: " + preview.take(3).joinToString(", ") { it.id } + " …")
                        Text("Last one: ${preview.last().id}")
                    }
                    Text(
                        "Aisles count from the left, spots from the front (bottom of the map).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FloorMap(site.copy(markers = preview, fingerprints = emptyList()))
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Replace ${site.markers.size} markers?") },
            text = { Text("Wi-Fi recordings at the same spots are kept. Markers you added by hand are removed.") },
            confirmButton = { TextButton(onClick = { confirm = false; make() }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
