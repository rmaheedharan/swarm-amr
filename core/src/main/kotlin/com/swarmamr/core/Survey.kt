package com.swarmamr.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** One Wi-Fi scan and when it arrived. */
data class TimedScan(val timeMs: Long, val rssi: Map<String, Double>)

/**
 * Walk-and-record: the user walks at a steady pace in a straight line from [from] to [to].
 * Each scan is placed on the line by its time, and scans are grouped into pieces about
 * [stepM] long. Each piece with at least one scan becomes a recording.
 */
object WalkRecording {
    /** A Wi-Fi scan reports signals measured over the last second or two, not at its arrival. */
    const val SCAN_LAG_MS = 1000L

    fun fingerprints(
        scans: List<TimedScan>,
        from: Vec2,
        to: Vec2,
        startMs: Long,
        endMs: Long,
        idPrefix: String,
        stepM: Double = 1.5,
    ): List<Fingerprint> {
        if (scans.isEmpty() || endMs <= startMs) return emptyList()
        val len = from.dist(to)
        val pieces = max(1, (len / stepM).roundToInt())
        val span = (endMs - startMs).toDouble()
        val groups = HashMap<Int, MutableList<Pair<Double, Map<String, Double>>>>()
        for (s in scans) {
            val f = ((s.timeMs - SCAN_LAG_MS - startMs) / span).coerceIn(0.0, 1.0)
            val i = (f * pieces).toInt().coerceAtMost(pieces - 1)
            groups.getOrPut(i) { mutableListOf() } += f to s.rssi
        }
        return groups.keys.sorted().map { i ->
            val g = groups.getValue(i)
            val f = g.sumOf { it.first } / g.size
            val at = from + (to - from) * f
            RssiPositioning.average(g.map { it.second }, at, "$idPrefix.${i + 1}")
        }
    }

    /** The next walk number: walks are saved as "W<n>.<piece>". */
    fun nextPrefix(prints: List<Fingerprint>): String {
        val n = prints.mapNotNull { WALK_ID.matchEntire(it.markerId)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "W${n + 1}"
    }

    fun isWalk(markerId: String) = WALK_ID.matches(markerId)

    private val WALK_ID = Regex("""W(\d+)\.\d+""")
}

/** How well each part of the floor is covered by Wi-Fi recordings. */
object Coverage {
    enum class Level { NONE, GOOD, ALIKE }

    data class Cell(val x: Double, val y: Double, val sizeM: Double, val level: Level) {
        val centre get() = Vec2(x + sizeM / 2, y + sizeM / 2)
    }

    data class Grid(val cells: List<Cell>) {
        val good get() = cells.count { it.level == Level.GOOD }
        val alike get() = cells.count { it.level == Level.ALIKE }
        /** Share of the floor that has a recording near it, 0 to 1. */
        val covered get() = if (cells.isEmpty()) 0.0 else (good + alike).toDouble() / cells.size
    }

    /**
     * Split the floor into squares. A square is GOOD when a recording lies within [reachM] of
     * it, ALIKE when the nearest such recording looks like another one far away, else NONE.
     */
    fun of(site: Site, reachM: Double = 2.0, maxCells: Int = 80): Grid {
        val g = site.grid
        val x0 = if (g.openFloor) g.originX else g.originX - g.halfPitch
        val x1 = if (g.openFloor) g.originX + g.widthM else g.originX + g.widthCells * g.halfPitch
        val y0 = g.bottomY()
        val y1 = g.topY()
        val size = max(1.0, max(x1 - x0, y1 - y0) / maxCells)
        val nx = ceil((x1 - x0) / size).toInt().coerceAtLeast(1)
        val ny = ceil((y1 - y0) / size).toInt().coerceAtLeast(1)
        val prints = site.fingerprints
        val alike = RssiPositioning.lookAlikes(prints).flatMap { listOf(it.first, it.second) }.toSet()
        val cells = ArrayList<Cell>(nx * ny)
        for (iy in 0 until ny) for (ix in 0 until nx) {
            val c = Cell(x0 + ix * size, y0 + iy * size, size, Level.NONE)
            val near = prints.minByOrNull { it.pos.dist(c.centre) }
            val level = when {
                near == null || near.pos.dist(c.centre) > reachM -> Level.NONE
                near in alike -> Level.ALIKE
                else -> Level.GOOD
            }
            cells += c.copy(level = level)
        }
        return Grid(cells)
    }
}
