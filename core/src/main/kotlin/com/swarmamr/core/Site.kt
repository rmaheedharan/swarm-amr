package com.swarmamr.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
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

/** A surveyed floor marker with a QR label; ground truth for the accuracy test. */
@Serializable
data class Marker(
    val id: String,
    val x: Double,
    val y: Double,
    val note: String = "",
) {
    val pos get() = Vec2(x, y)
}

/** Mean RSSI per BSSID recorded at one point during the survey walk. */
@Serializable
data class Fingerprint(
    val x: Double,
    val y: Double,
    val rssi: Map<String, Double>,
    val markerId: String = "",
    val samples: Int = 1,
) {
    val pos get() = Vec2(x, y)
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
) {
    fun ap(bssid: String) = accessPoints.firstOrNull { it.bssid.equals(bssid, ignoreCase = true) }
    fun marker(id: String) = markers.firstOrNull { it.id == id }

    fun toJson(): String = SiteJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(text: String): Site = SiteJson.decodeFromString(serializer(), text)

        /** Markers every 2 cells along each aisle, named A<aisle>-<row>, e.g. "A3-07". */
        fun defaultMarkers(g: GridSpec): List<Marker> = buildList {
            for (a in 0 until g.nAisles) {
                for (row in 1..g.aisleLenCells step 2) {
                    add(Marker("A$a-%02d".format(row), g.aisleX(a), g.originY + row * g.cellM))
                }
            }
        }
    }
}

val SiteJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun nearlyEqual(a: Double, b: Double, eps: Double = 1e-9) = abs(a - b) < eps
