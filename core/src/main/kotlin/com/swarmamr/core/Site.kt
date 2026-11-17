package com.swarmamr.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A point in the site frame, metres. */
@Serializable
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)
    fun dist(o: Vec2) = kotlin.math.hypot(x - o.x, y - o.y)
}

/**
 * The racking layout as a grid, shared with the backend engine (backend/swarm_server/warehouse.py).
 *
 * Pick aisles run along y. Aisle `a` has its centreline at
 * x = originX + (2a + 1) * aislePitchM / 2. Two cross-aisles run along x at the two ends:
 * y = originY (the "bottom", grid row 0) and y = originY + (aisleLenCells + 1) * cellM (the "top").
 */
@Serializable
data class GridSpec(
    val originX: Double = 0.0,
    val originY: Double = 0.0,
    val aislePitchM: Double = 3.0,
    val cellM: Double = 1.2,
    val nAisles: Int = 8,
    val aisleLenCells: Int = 12,
    val narrowAisles: List<Int> = listOf(1, 2, 5, 6),
    /**
     * An open floor with no fixed walkways (an office, a shop floor). Positions are not
     * snapped to aisles; the floor is a rectangle [widthM] by [depthM], split into
     * [ZONE_M] metre zones for the "right area" score.
     */
    val openFloor: Boolean = false,
) {
    val halfPitch get() = aislePitchM / 2
    val topRow get() = aisleLenCells + 1
    val widthCells get() = 2 * nAisles + 1

    fun aisleX(a: Int) = originX + (2 * a + 1) * halfPitch
    fun bottomY() = originY
    fun topY() = originY + topRow * cellM

    /** Grid cell (column, row) of a site point, clamped to the grid. */
    fun toCell(p: Vec2): Pair<Int, Int> {
        val cx = ((p.x - originX) / halfPitch).roundToInt().coerceIn(0, widthCells - 1)
        val cy = ((p.y - originY) / cellM).roundToInt().coerceIn(0, topRow)
        return cx to cy
    }

    fun cellCentre(cx: Int, cy: Int) = Vec2(originX + cx * halfPitch, originY + cy * cellM)

    fun isNarrow(aisle: Int) = aisle in narrowAisles

    val widthM get() = nAisles * aislePitchM
    val depthM get() = topY() - bottomY()

    /** Zone name of a point on an open floor, e.g. "Z2-3" (4 m squares from the origin). */
    fun zoneOf(p: Vec2): String {
        val ix = ((p.x - originX) / ZONE_M).toInt().coerceIn(0, maxOf(0, (widthM / ZONE_M).toInt()))
        val iy = ((p.y - originY) / ZONE_M).toInt().coerceIn(0, maxOf(0, (depthM / ZONE_M).toInt()))
        return "Z$ix-$iy"
    }

    companion object {
        const val ZONE_M = 4.0

        /** An open floor of about [widthM] by [depthM] metres, 1 m steps. */
        fun open(widthM: Int, depthM: Int, originX: Double = 0.0, originY: Double = 0.0) = GridSpec(
            originX = originX, originY = originY, aislePitchM = 1.0, cellM = 1.0,
            nAisles = widthM.coerceAtLeast(2), aisleLenCells = (depthM - 1).coerceAtLeast(1),
            narrowAisles = emptyList(), openFloor = true,
        )
    }
}

@Serializable
data class AccessPoint(
    val bssid: String,               // lower case, colon separated
    val x: Double,
    val y: Double,
    val label: String = "",
    val rttBiasM: Double = 0.0,      // measured during calibration, subtracted from RTT ranges
    val frequencyMhz: Int = 0,       // optional, for building RTT requests without a scan
) {
    val pos get() = Vec2(x, y)
}

/**
 * A surveyed floor point; ground truth for the accuracy test.
 * [id] is the text inside its QR code (any text, for example a desk number), or a typed name.
 * [label] is an optional friendly name shown instead of the id.
 */
@Serializable
data class Marker(
    val id: String,
    val x: Double,
    val y: Double,
    val note: String = "",
    val label: String = "",
) {
    val pos get() = Vec2(x, y)
    val name get() = label.ifBlank { id }
}

/**
 * Names for automatic markers, from a pattern. In the pattern:
 * - `{A}` is the aisle number, `{S}` the spot number in that aisle, `{N}` a running number
 *   over the whole place, `{R}` the grid row. Repeat the letter for leading zeros:
 *   `{AA}` gives 04, `{NNNN}` gives 0012.
 * - Everything else is kept as it is. "L{AA}{SS}" gives L0432 for aisle 4, spot 32.
 *
 * Aisles are counted from the left (x), spots from the bottom (y), starting at [firstAisle]
 * and [firstSpot]. One spot every [everyCells] grid cells.
 */
@Serializable
data class MarkerNaming(
    val pattern: String = "A{A}-{RR}",
    val firstAisle: Int = 0,
    val firstSpot: Int = 1,
    val everyCells: Int = 2,
) {
    fun name(aisle: Int, spot: Int, running: Int, row: Int): String =
        TOKEN.replace(pattern) { m ->
            val t = m.groupValues[1]
            val v = when (t[0].uppercaseChar()) {
                'A' -> aisle
                'S' -> spot
                'N' -> running
                else -> row
            }
            v.toString().padStart(t.length, '0')
        }

    /** One marker every [everyCells] cells along each aisle, named by the pattern. */
    fun markers(g: GridSpec): List<Marker> = build(g).distinctBy { it.id }

    /** True when the pattern gives every marker its own name. */
    fun isUnique(g: GridSpec): Boolean = build(g).let { l -> l.size == l.map { it.id }.toSet().size }

    private fun build(g: GridSpec): List<Marker> = buildList {
        val step = everyCells.coerceAtLeast(1)
        var n = firstSpot
        for (a in 0 until g.nAisles) {
            var spot = firstSpot
            for (row in (step + 1) / 2..g.aisleLenCells step step) {
                add(Marker(name(firstAisle + a, spot, n, row), g.aisleX(a), g.originY + row * g.cellM))
                spot++; n++
            }
        }
    }

    companion object {
        private val TOKEN = Regex("""\{([AaSsNnRr]+)\}""")
    }
}

/** QR code text to marker id. Old labels carry "SWM:<id>"; any other text is used as it is. */
object MarkerCodes {
    fun parse(text: String?): String? = text?.trim()?.removePrefix("SWM:")?.trim()?.takeIf { it.isNotEmpty() }
}

/** Mean RSSI per BSSID recorded at one point during the survey walk. */
@Serializable
data class Fingerprint(
    val x: Double,
    val y: Double,
    val rssi: Map<String, Double>,
    val markerId: String = "",
    val samples: Int = 1,
    /** How much each AP's signal moved during the recording (standard deviation, dB). */
    val std: Map<String, Double> = emptyMap(),
) {
    val pos get() = Vec2(x, y)
}

/**
 * A picture of the floor plan, drawn under the map. Its bottom-left corner sits at the grid
 * origin, and [metresPerPx] (set by marking one known length) turns pixels into metres.
 * [file] is a file name in the app's own storage; the picture itself is not in the site file.
 */
@Serializable
data class FloorImage(
    val file: String,
    val widthPx: Int,
    val heightPx: Int,
    val metresPerPx: Double,
) {
    val widthM get() = widthPx * metresPerPx
    val heightM get() = heightPx * metresPerPx

    /** Site point of a picture pixel (pixel y grows down, site y grows up). */
    fun toSite(px: Double, py: Double, origin: Vec2 = Vec2(0.0, 0.0)) =
        Vec2(origin.x + px * metresPerPx, origin.y + (heightPx - py) * metresPerPx)

    /** An open floor just big enough to hold the picture. */
    fun grid(originX: Double = 0.0, originY: Double = 0.0) =
        GridSpec.open(ceil(widthM).toInt(), ceil(heightM).toInt(), originX, originY)

    companion object {
        /** Scale from two points on the picture (pixels) that are [lengthM] apart in real life. */
        fun metresPerPx(a: Vec2, b: Vec2, lengthM: Double): Double? {
            val d = a.dist(b)
            if (d < 1.0 || lengthM <= 0.0) return null
            return lengthM / d
        }
    }
}

@Serializable
data class Site(
    val id: String = "demo",
    val name: String = "Demo site",
    val grid: GridSpec = GridSpec(),
    val accessPoints: List<AccessPoint> = emptyList(),
    val markers: List<Marker> = emptyList(),
    val fingerprints: List<Fingerprint> = emptyList(),
    val version: Int = 1,
    /** How automatic markers are named, so a new layout keeps the place's own codes. */
    val naming: MarkerNaming = MarkerNaming(),
    /** Optional floor plan picture drawn under the map. */
    val image: FloorImage? = null,
) {
    fun ap(bssid: String) = accessPoints.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }
    fun marker(id: String) = markers.firstOrNull { it.id == id }
        ?: markers.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }

    /** Markers with a Wi-Fi survey recording. */
    fun surveyed(m: Marker) = fingerprints.any { it.markerId == m.id }

    fun toJson(): String = SiteJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(text: String): Site = SiteJson.decodeFromString(serializer(), text)

        /** Markers every 2 cells along each aisle, named A<aisle>-<row>, e.g. "A3-07". */
        fun defaultMarkers(g: GridSpec): List<Marker> = MarkerNaming().markers(g)
    }
}

val SiteJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun nearlyEqual(a: Double, b: Double, eps: Double = 1e-9) = abs(a - b) < eps

/** Guided walk: which marker to visit next. */
object WalkPlan {
    /** The marker nearest to [from] that is not in [done]; the first one in list order when [from] is null. */
    fun next(markers: List<Marker>, done: Set<String>, from: Vec2?, skip: Set<String> = emptySet()): Marker? {
        val left = markers.filter { it.id !in done && it.id !in skip }
            .ifEmpty { markers.filter { it.id !in done } }
        if (left.isEmpty()) return null
        return if (from == null) left.first() else left.minBy { it.pos.dist(from) }
    }
}
