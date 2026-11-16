package com.swarmamr.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swarmamr.core.Estimate
import com.swarmamr.core.Marker
import com.swarmamr.core.Site
import com.swarmamr.core.Vec2
import kotlin.math.max
import kotlin.math.min

/** Zoom and pan of a [FloorMap], kept outside so buttons can zoom or follow the user. */
@Stable
class MapState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    /** Keep the user's dot in the middle of the map. Dragging the map turns it off. */
    var follow by mutableStateOf(false)
    internal var size by mutableStateOf(IntSize.Zero)

    internal fun clamp(o: Offset, s: Float): Offset {
        val w = size.width.toFloat(); val h = size.height.toFloat()
        return Offset(o.x.coerceIn(min(0f, w * (1 - s)), 0f), o.y.coerceIn(min(0f, h * (1 - s)), 0f))
    }

    fun zoomAt(p: Offset, factor: Float) {
        val s = (scale * factor).coerceIn(1f, MAX_ZOOM)
        offset = clamp((offset - p) * (s / scale) + p, s)
        scale = s
    }

    fun zoomBy(factor: Float) = zoomAt(Offset(size.width / 2f, size.height / 2f), factor)

    fun reset() { scale = 1f; offset = Offset.Zero; follow = false }

    companion object { const val MAX_ZOOM = 12f }
}

@Composable
fun rememberMapState() = remember { MapState() }

/**
 * Top view of the place: walkways, narrow aisles (tinted), markers, access points, me.
 * Pinch to zoom, drag to move, double tap to zoom in. Taps report the site position, or the
 * marker under the finger.
 */
@Composable
fun FloorMap(
    site: Site,
    modifier: Modifier = Modifier,
    me: Estimate? = null,
    target: Vec2? = null,
    highlight: String? = null,
    pin: Vec2? = null,
    done: Set<String> = emptySet(),
    showAccessPoints: Boolean = false,
    onTap: ((Vec2) -> Unit)? = null,
    onMarkerTap: ((Marker) -> Unit)? = null,
    state: MapState = rememberMapState(),
    fill: Boolean = false,
) {
    val g = site.grid
    var minX = g.originX - g.halfPitch
    var maxX = g.originX + g.widthCells * g.halfPitch
    var minY = g.bottomY() - g.cellM
    var maxY = g.topY() + g.cellM
    for (mk in site.markers) {
        minX = min(minX, mk.x - 1); maxX = max(maxX, mk.x + 1)
        minY = min(minY, mk.y - 1); maxY = max(maxY, mk.y + 1)
    }
    val aspect = ((maxX - minX) / (maxY - minY)).toFloat().coerceIn(0.6f, 2.2f)

    val scheme = MaterialTheme.colorScheme
    val walk = scheme.surfaceContainerHighest
    val rack = scheme.surfaceContainerLow
    val ink = scheme.onSurfaceVariant
    val primary = scheme.primary
    val narrowTint = Color(0x40E09B00)
    val doneColor = GoodGreen
    val targetColor = Color(0xFFD81B60)
    val apColor = scheme.tertiary
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 11.sp, color = ink)
    val boldStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface, fontWeight = FontWeight.Bold)
    val touchPx = with(density) { 24.dp.toPx() }

    val tap by rememberUpdatedState(onTap)
    val markerTap by rememberUpdatedState(onMarkerTap)
    val markers by rememberUpdatedState(site.markers)

    // Base mapping (zoom 1): the whole place fits the box and is centred. Then the zoom.
    fun baseScale(): Float {
        val w = state.size.width.toFloat(); val h = state.size.height.toFloat()
        if (w <= 0f || h <= 0f) return 1f
        return min(w / (maxX - minX).toFloat(), h / (maxY - minY).toFloat())
    }
    fun baseOrigin(): Offset {
        val k = baseScale()
        return Offset(
            (state.size.width - (maxX - minX).toFloat() * k) / 2f,
            (state.size.height - (maxY - minY).toFloat() * k) / 2f,
        )
    }
    fun toBase(v: Vec2): Offset {
        val k = baseScale(); val o = baseOrigin()
        return Offset(o.x + ((v.x - minX) * k).toFloat(), o.y + ((maxY - v.y) * k).toFloat())
    }
    fun toScreen(v: Vec2): Offset = toBase(v) * state.scale + state.offset
    fun toSite(p: Offset): Vec2 {
        val k = baseScale(); val o = baseOrigin()
        val b = (p - state.offset) / state.scale
        return Vec2(minX + (b.x - o.x) / k, maxY - (b.y - o.y) / k)
    }

    // Follow mode: keep "me" in the middle, zoomed in enough to see the aisles around.
    val mePos = me?.snapped?.pos
    LaunchedEffect(mePos, state.follow, state.size) {
        if (!state.follow || mePos == null || state.size == IntSize.Zero) return@LaunchedEffect
        val s = if (state.scale < 2.5f) 3f else state.scale
        val centre = Offset(state.size.width / 2f, state.size.height / 2f)
        state.offset = state.clamp(centre - toBase(mePos) * s, s)
        state.scale = s
    }

    val frame = if (fill) modifier.fillMaxSize() else modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(16.dp))
    Box(
        frame.background(rack)
            .onSizeChanged { state.size = it }
            .pointerInput(state) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (pan.getDistance() > 2f) state.follow = false
                    val s = (state.scale * zoom).coerceIn(1f, MapState.MAX_ZOOM)
                    val o = (state.offset - centroid) * (s / state.scale) + centroid + pan
                    state.scale = s
                    state.offset = state.clamp(o, s)
                }
            }
            .pointerInput(state, minX, maxX, minY, maxY) {
                detectTapGestures(
                    onDoubleTap = { p -> state.zoomAt(p, 2f) },
                    onTap = { p ->
                        val hit = markers.minByOrNull { (toScreen(it.pos) - p).getDistance() }
                        val mt = markerTap
                        if (mt != null && hit != null && (toScreen(hit.pos) - p).getDistance() < touchPx) mt(hit)
                        else tap?.invoke(toSite(p))
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = baseScale() * state.scale
            fun rect(x0: Double, y0: Double, x1: Double, y1: Double, c: Color) {
                val a = toScreen(Vec2(x0, y1)); val b = toScreen(Vec2(x1, y0))
                drawRect(c, a, Size(b.x - a.x, b.y - a.y))
            }
            // Walkways: the two cross-aisles and every aisle.
            val half = g.halfPitch / 2
            rect(g.originX - half, g.bottomY() - g.cellM / 2, g.originX + (g.widthCells - 1) * g.halfPitch + half, g.bottomY() + g.cellM / 2, walk)
            rect(g.originX - half, g.topY() - g.cellM / 2, g.originX + (g.widthCells - 1) * g.halfPitch + half, g.topY() + g.cellM / 2, walk)
            for (a in 0 until g.nAisles) {
                val x = g.aisleX(a)
                rect(x - half, g.bottomY(), x + half, g.topY(), if (a in g.narrowAisles) narrowTint else walk)
                val lab = measurer.measure("A$a", labelStyle)
                val c = toScreen(Vec2(x, g.topY()))
                drawText(lab, topLeft = Offset(c.x - lab.size.width / 2f, c.y - lab.size.height / 2f))
            }
            if (showAccessPoints) for (ap in site.accessPoints) {
                val c = toScreen(ap.pos)
                val path = Path().apply { moveTo(c.x, c.y - 10f); lineTo(c.x + 9f, c.y + 7f); lineTo(c.x - 9f, c.y + 7f); close() }
                drawPath(path, apColor)
            }
            // Markers; names are drawn when there is room for them.
            val spacious = k * g.cellM * 2 > 60
            for (mk in site.markers) {
                val c = toScreen(mk.pos)
                val isDone = mk.id in done
                val hi = mk.id == highlight
                if (hi) {
                    drawCircle(targetColor.copy(alpha = 0.18f), 26f, c)
                    drawCircle(targetColor, 11f, c)
                    drawCircle(Color.White, 4f, c)
                } else {
                    drawCircle(if (isDone) doneColor else ink.copy(alpha = 0.55f), if (isDone) 7f else 5f, c)
                }
                if (hi || spacious) {
                    val lab = measurer.measure(mk.name, if (hi) boldStyle else labelStyle)
                    drawText(lab, topLeft = Offset(c.x + 12f, c.y - lab.size.height / 2f))
                }
            }
            target?.let {
                val c = toScreen(it)
                drawCircle(targetColor, 12f, c, style = Stroke(4f))
                drawCircle(targetColor, 4f, c)
            }
            pin?.let {
                val c = toScreen(it)
                val path = Path().apply {
                    moveTo(c.x, c.y); lineTo(c.x - 12f, c.y - 22f); lineTo(c.x + 12f, c.y - 22f); close()
                }
                drawCircle(targetColor, 14f, Offset(c.x, c.y - 26f))
                drawPath(path, targetColor)
                drawCircle(Color.White, 5f, Offset(c.x, c.y - 26f))
            }
            me?.let { e ->
                val c = toScreen(e.snapped.pos)
                val r = (e.sigmaM * k).toFloat().coerceAtLeast(14f)
                drawCircle(primary.copy(alpha = 0.16f), r, c)
                drawCircle(Color.White, 13f, c)
                drawCircle(primary, 10f, c)
            }
        }
        if (!fill && state.scale > 1.01f) {
            FilledTonalIconButton(
                onClick = { state.reset() },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) { Icon(Icons.Filled.ZoomOutMap, "Show whole map") }
        }
    }
}
