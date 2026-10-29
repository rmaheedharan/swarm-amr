package com.swarmamr.core

import kotlin.random.Random

/** One stop on the demo pick list: a surveyed marker, or the pack station. */
data class PickStop(val label: String, val pos: Vec2)

/**
 * A demo pick list for swarm mode: random markers, walked in batches of [batchSize] in aisle
 * order, each batch ending at the pack station. The app marks a pick done when the worker
 * has stood within [arriveM] of it for [dwellS] seconds, or when the worker taps "Next".
 */
class DemoPickList(
    site: Site,
    seed: Int = Random.nextInt(),
    count: Int = 48,
    private val batchSize: Int = 6,
    private val arriveM: Double = 1.8,
    private val dwellS: Int = 6,
) {
    private val stops = mutableListOf<PickStop>()
    var index = 0; private set
    private var arrivedAtMs = 0L

    init {
        val rnd = Random(seed)
        val g = site.grid
        val pack = PickStop("Pack", g.cellCentre(0, 0))
        val markers = site.markers.ifEmpty { Site.defaultMarkers(g) }
        repeat(count / batchSize) {
            val batch = List(batchSize) { markers[rnd.nextInt(markers.size)] }
                .distinctBy { it.id }
                .sortedWith(compareBy({ g.toCell(it.pos).first }, { m ->
                    val (cx, cy) = g.toCell(m.pos)
                    if ((cx / 2) % 2 == 0) cy else -cy                   // S-shape route
                }))
            batch.forEach { stops += PickStop(it.id, it.pos) }
            stops += pack
        }
    }

    val current get() = stops.getOrNull(index)
    val done get() = index
    val total get() = stops.size

    fun upcoming(n: Int): List<PickStop> = stops.drop(index).take(n)

    /** Returns true while the worker is standing at the pick (picking). */
    fun update(pos: Vec2?, nowMs: Long): Boolean {
        val c = current ?: return false
        if (pos == null || pos.dist(c.pos) > arriveM) {
            arrivedAtMs = 0
            return false
        }
        if (arrivedAtMs == 0L) arrivedAtMs = nowMs
        if (nowMs - arrivedAtMs >= dwellS * 1000L) {
            next()
            return false
        }
        return true
    }

    fun next() {
        if (index < stops.size) index++
        arrivedAtMs = 0
    }

    /** Resequence cue: do upcoming stop [offset] (1 = the one after the current) first. */
    fun bringForward(offset: Int) {
        val j = index + offset
        if (offset <= 0 || j >= stops.size) return
        stops.add(index, stops.removeAt(j))
        arrivedAtMs = 0
    }
}
