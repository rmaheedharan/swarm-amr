package com.swarmamr.core

import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Simulated survey walk and test walk along one aisle, like a first test at home:
 * fingerprints every 2.4 m, then walk from the first marker to the last and stand still.
 * This is a simulation of the algorithm only, not a floor measurement.
 */
class FingerprintWalkTest {
    private val aps = mapOf(
        "ap1" to Vec2(1.0, 1.0), "ap2" to Vec2(11.0, 2.0), "ap3" to Vec2(2.0, 12.0),
        "ap4" to Vec2(9.0, 13.0), "ap5" to Vec2(6.0, 7.0), "ap6" to Vec2(14.0, 9.0),
    )
    private val aisleX = 10.5
    private val rows = listOf(1.2, 3.6, 6.0, 8.4, 10.8)

    /** Log-distance path loss with fixed shadowing per AP and place, plus scan noise. */
    private fun scan(p: Vec2, rnd: Random, noiseDb: Double): Map<String, Double> = aps.mapValues { (id, ap) ->
        val d = max(hypot(p.x - ap.x, p.y - ap.y), 0.5)
        val shadow = 3.0 * kotlin.math.sin(p.y * 1.3 + id.hashCode()) // walls and furniture
        -35.0 - 30.0 * log10(d) + shadow + rnd.nextDouble(-noiseDb, noiseDb) * 1.7
    }

    private fun survey(rnd: Random) = rows.mapIndexed { i, y ->
        val at = Vec2(aisleX, y)
        RssiPositioning.average(List(6) { scan(at, rnd, 4.0) }, at, "A3-%02d".format(2 * i + 1))
    }

    /** Final error after walking from the first marker to the last and standing still for [standS]. */
    private fun walkError(seed: Int, standS: Int): Double {
        val rnd = Random(seed)
        val g = GridSpec(nAisles = 4, aisleLenCells = 10)
        val site = Site(grid = g, fingerprints = survey(rnd))
        val f = PositionFuser(site)
        var t = 1_000L
        val scanEveryMs = 3_000L
        var nextScan = t
        val start = rows.first(); val end = rows.last()
        val walkMs = ((end - start) / 1.0 * 1000).toLong()
        val stopAt = t + walkMs
        val endAt = stopAt + standS * 1000L
        while (t <= endAt) {
            val walking = t < stopAt
            f.setStationary(!walking)
            if (t >= nextScan) {
                val y = if (walking) start + (t - 1_000L) / 1000.0 else end
                f.onScan(scan(Vec2(aisleX, y), rnd, 4.0), t)
                nextScan += scanEveryMs
            }
            f.tick(t)
            t += 100
        }
        return f.estimate(t)!!.raw.dist(Vec2(aisleX, end))
    }

    @Test
    fun standingAtTheEndOfTheAisleConverges() {
        val errs = (1..40).map { walkError(it, standS = 20) }.sorted()
        val median = errs[errs.size / 2]
        println("end-of-aisle error after 20 s standing (simulation): median %.2f m, worst %.2f m".format(median, errs.last()))
        assertTrue(median < 1.5, "median error $median m")
    }
}
