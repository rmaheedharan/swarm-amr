package com.swarmamr.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swarmamr.core.FloorImage
import com.swarmamr.core.GridSpec
import com.swarmamr.core.Site
import com.swarmamr.core.Vec2

/**
 * Set the scale of a floor plan picture: tap both ends of something whose real length you
 * know (a long table, a wall with a measurement), then type that length.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScaleScreen(s: Screen.PlanScale) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val snackHost = LocalSnack.current

    // Work in picture pixels: a site with 1 "metre" per pixel shows the picture as it is.
    val pixelSite = remember(s) {
        Site(grid = GridSpec.open(s.widthPx, s.heightPx), image = FloorImage(s.file, s.widthPx, s.heightPx, 1.0))
    }
    val mapState = rememberMapState()
    var a by remember { mutableStateOf<Vec2?>(null) }
    var b by remember { mutableStateOf<Vec2?>(null) }
    var lengthText by remember { mutableStateOf("") }
    var cm by remember { mutableStateOf(true) }
    val length = parseNum(lengthText)?.let { if (cm) it / 100 else it }
    val pa = a
    val pb = b
    val mpp = if (pa != null && pb != null && length != null) FloorImage.metresPerPx(pa, pb, length) else null

    fun save() {
        val k = mpp ?: return
        val img = FloorImage(s.file, s.widthPx, s.heightPx, k)
        app.sites.setImage(img)
        app.settings.update { it.copy(floorPlanSaved = true) }
        app.scope.snack(snackHost, "Floor plan saved: about ${m(img.widthM, 0)} by ${m(img.heightM, 0)}. Now tap where you stand.")
        // Back past the floor plan screen, then straight to recording.
        while (nav.top != null && nav.top != Screen.QuickRecord) nav.pop()
        if (nav.top == null) nav.push(Screen.QuickRecord)
    }

    AppScaffold(title = "Set the scale", back = true) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            FloorMap(
                pixelSite,
                state = mapState,
                fill = true,
                line = a?.let { it to b },
                onTap = { p ->
                    when {
                        a == null -> a = p
                        b == null -> b = p
                        else -> { a = p; b = null }
                    }
                },
            )
            ElevatedCard(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            a == null -> "Tap one end of something you know the length of"
                            b == null -> "Now tap the other end"
                            else -> "Type its real length"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "For example a long table, or a wall with its size written on the plan. " +
                            "The longer, the better. Pinch to zoom in for an exact tap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (a != null && b != null) {
                ElevatedCard(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().padding(12.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                lengthText, { lengthText = it },
                                label = { Text("Real length") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(selected = cm, onClick = { cm = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("cm") }
                                SegmentedButton(selected = !cm, onClick = { cm = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("m") }
                            }
                        }
                        val k = mpp
                        Text(
                            if (k == null) "Plans often show sizes in cm, like 1071."
                            else "The whole floor is about ${m(s.widthPx * k, 0)} wide and ${m(s.heightPx * k, 0)} deep.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { a = null; b = null }) { Text("Start again") }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { save() }, enabled = k != null) { Text("Save") }
                        }
                    }
                }
            }
        }
    }
}
