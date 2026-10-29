package com.swarmamr.core

import kotlin.math.abs

/** Where on the walkable network a point snaps to. */
data class Snap(
    val pos: Vec2,
    val aisle: Int?,          // pick aisle index, or null on a cross-aisle
    val offsetM: Double,      // how far the raw point was from the walkable line
) {
    val area get() = aisle?.let { "A$it" } ?: "cross"
}

/**
 * Snaps positions onto aisle centrelines and the two cross-aisles. People cannot walk
 * through racking, so this removes most sideways jitter. A change of aisle is only accepted
 * after [confirmUpdates] updates in a row (hysteresis), unless forced by a QR scan.
 */
class MapMatcher(private val grid: GridSpec, private val confirmUpdates: Int = 2) {
    private var current: Int? = null
    private var candidate: Int? = null
    private var candidateCount = 0

    private val minX get() = grid.originX
    private val maxX get() = grid.originX + (grid.widthCells - 1) * grid.halfPitch

    private fun onCross(p: Vec2): Snap {
        val x = p.x.coerceIn(minX, maxX)
        val bottom = abs(p.y - grid.bottomY())
        val top = abs(p.y - grid.topY())
        val y = if (top < bottom) grid.topY() else grid.bottomY()
        return Snap(Vec2(x, y), null, Vec2(x, y).dist(p))
    }

    private fun onAisle(p: Vec2, a: Int): Snap {
        val q = Vec2(grid.aisleX(a), p.y.coerceIn(grid.bottomY(), grid.topY()))
        return Snap(q, a, q.dist(p))
    }

    fun snapRaw(p: Vec2): Snap {
        var best = onCross(p)
        for (a in 0 until grid.nAisles) {
            val s = onAisle(p, a)
            if (s.offsetM < best.offsetM) best = s
        }
        return best
    }

    /** Snap with hysteresis on the aisle. */
    fun snap(p: Vec2): Snap {
        val raw = snapRaw(p)
        if (raw.aisle == current) {
            candidate = null; candidateCount = 0
            return raw
        }
        if (raw.aisle == candidate) candidateCount++ else { candidate = raw.aisle; candidateCount = 1 }
        if (candidateCount >= confirmUpdates) {
            current = raw.aisle
            candidate = null; candidateCount = 0
            return raw
        }
        // Not confirmed yet: stay where we were.
        return current?.let { onAisle(p, it) } ?: onCross(p)
    }

    /** A QR scan or other certain fix: jump to it without hysteresis. */
    fun force(p: Vec2): Snap {
        val raw = snapRaw(p)
        current = raw.aisle
        candidate = null; candidateCount = 0
        return raw
    }

    fun aisleOf(p: Vec2): Int? = snapRaw(p).aisle
}
