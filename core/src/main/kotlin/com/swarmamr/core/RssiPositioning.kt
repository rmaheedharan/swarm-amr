package com.swarmamr.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Positioning from Wi-Fi signal strength (RSSI), for sites whose APs do not support RTT.
 *
 * 1. Fingerprinting (preferred): during the survey walk the app stores the mean RSSI of every
 *    AP at each marker. Live, we find the k most similar stored fingerprints and average their
 *    positions, weighted by similarity.
 * 2. Weighted centroid (fallback, no survey yet): average the positions of the strongest known
 *    APs, weighted by signal power. Rough: expect several metres of error.
 */
object RssiPositioning {
    const val MISSING_DBM = -100.0
    private const val MIN_COMMON = 3
    private const val NEAR_DB = 3.0      // neighbours this much worse than the best match are ignored
    private const val KERNEL_DB = 1.0    // weight falls to 60% at 1 dB worse than the best match

    fun fingerprint(scan: Map<String, Double>, prints: List<Fingerprint>, k: Int = 3): Fix? {
        if (prints.isEmpty() || scan.isEmpty()) return null
        val scored = prints.mapNotNull { fp ->
            val keys = fp.rssi.keys union scan.keys
            val common = fp.rssi.keys.count { it in scan }
            if (common < MIN_COMMON) return@mapNotNull null
            val d2 = keys.sumOf { b ->
                val a = scan[b] ?: MISSING_DBM
                val c = fp.rssi[b] ?: MISSING_DBM
                (a - c) * (a - c)
            }
            fp to sqrt(d2 / keys.size)          // RMS dB difference
        }.sortedBy { it.second }
        if (scored.isEmpty()) return null
        // Keep only neighbours that match almost as well as the best one, and weight them sharply.
        // A plain average of the k best pulls positions at the end of an aisle toward its middle.
        val best = scored.first().second
        val near = scored.take(k).filter { it.second - best <= NEAR_DB }
        val w = near.map { exp(-0.5 * ((it.second - best) / KERNEL_DB).pow(2)) }
        val sw = w.sum()
        val pos = Vec2(
            near.indices.sumOf { near[it].first.x * w[it] } / sw,
            near.indices.sumOf { near[it].first.y * w[it] } / sw,
        )
        // Spread of the neighbours is a fair stand-in for uncertainty, floored at 1.2 m.
        val spread = near.indices.sumOf { w[it] * near[it].first.pos.dist(pos).pow(2) } / sw
        val v = max(spread, 1.2 * 1.2)
        return Fix(pos, v, 0.0, v, best, near.size, Fix.Source.FINGERPRINT)
    }

    fun weightedCentroid(scan: Map<String, Double>, site: Site, strongest: Int = 4): Fix? {
        val seen = scan.mapNotNull { (b, dbm) -> site.ap(b)?.let { it to dbm } }
            .sortedByDescending { it.second }.take(strongest)
        if (seen.size < 2) return null
        val w = seen.map { 10.0.pow(it.second / 10.0) }       // linear power
        val sw = w.sum()
        val pos = Vec2(
            seen.indices.sumOf { seen[it].first.x * w[it] } / sw,
            seen.indices.sumOf { seen[it].first.y * w[it] } / sw,
        )
        val v = 4.0 * 4.0
        return Fix(pos, v, 0.0, v, 0.0, seen.size, Fix.Source.RSSI_CENTROID)
    }

    /** Average several scans taken at one spot into one fingerprint. */
    fun average(scans: List<Map<String, Double>>, at: Vec2, markerId: String = "", minSeen: Double = 0.5): Fingerprint {
        val all = scans.flatMap { it.keys }.toSet()
        val mean = all.mapNotNull { b ->
            val vals = scans.mapNotNull { it[b] }
            if (vals.size < minSeen * scans.size) null else b to vals.average()
        }.toMap()
        return Fingerprint(at.x, at.y, mean, markerId, scans.size)
    }
}
