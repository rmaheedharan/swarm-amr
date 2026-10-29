package com.swarmamr.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.swarmamr.core.Estimate
import com.swarmamr.core.Site
import com.swarmamr.core.Vec2

/** Top view of the site: racking, narrow aisles (tinted), markers, APs, me, next pick. */
@Composable
fun FloorMap(site: Site, est: Estimate?, target: Vec2?, modifier: Modifier = Modifier) {
    val g = site.grid
    val minX = g.originX - g.halfPitch
    val maxX = g.originX + g.widthCells * g.halfPitch
    val minY = g.bottomY() - g.cellM
    val maxY = g.topY() + g.cellM
    val aspect = ((maxX - minX) / (maxY - minY)).toFloat().coerceIn(0.4f, 2.5f)
    val rack = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val narrowTint = Color(0x33E09B00)
    val apColor = Color(0xFF00897B)
    val targetColor = Color(0xFFD81B60)

    Canvas(modifier.fillMaxWidth().aspectRatio(aspect)) {
        val sx = size.width / (maxX - minX).toFloat()
        val sy = size.height / (maxY - minY).toFloat()
        fun p(v: Vec2) = Offset(((v.x - minX) * sx).toFloat(), ((maxY - v.y) * sy).toFloat())
        fun pxy(x: Double, y: Double) = p(Vec2(x, y))

        // Racking between aisles (everything that is not aisle or cross-aisle).
        for (col in 0 until g.widthCells step 2) {
            val x0 = g.originX + col * g.halfPitch - g.halfPitch / 2
            val tl = pxy(x0, g.topY() - g.cellM / 2)
            val br = pxy(x0 + g.halfPitch, g.bottomY() + g.cellM / 2)
            drawRect(rack, tl, Size(br.x - tl.x, br.y - tl.y))
        }
        for (a in g.narrowAisles) {
            if (a !in 0 until g.nAisles) continue
            val x0 = g.aisleX(a) - g.halfPitch / 2
            val tl = pxy(x0, g.topY() - g.cellM / 2)
            val br = pxy(x0 + g.halfPitch, g.bottomY() + g.cellM / 2)
            drawRect(narrowTint, tl, Size(br.x - tl.x, br.y - tl.y))
        }
        for (mk in site.markers) drawCircle(ink.copy(alpha = 0.35f), 3f, p(mk.pos))
        for (ap in site.accessPoints) {
            val c = p(ap.pos)
            val path = Path().apply {
                moveTo(c.x, c.y - 9f); lineTo(c.x + 8f, c.y + 6f); lineTo(c.x - 8f, c.y + 6f); close()
            }
            drawPath(path, apColor)
        }
        target?.let {
            val c = p(it)
            drawCircle(targetColor, 9f, c, style = Stroke(3f))
            drawCircle(targetColor, 3f, c)
        }
        est?.let { e ->
            val c = p(e.snapped.pos)
            val r = (e.sigmaM * sx).toFloat().coerceAtLeast(6f)
            drawCircle(primary.copy(alpha = 0.18f), r, c)
            drawCircle(primary.copy(alpha = 0.5f), 4f, p(e.raw), style = Stroke(2f))
            drawCircle(primary, 8f, c)
        }
    }
}
