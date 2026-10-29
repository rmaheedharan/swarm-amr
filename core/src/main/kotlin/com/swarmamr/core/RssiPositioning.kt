package com.swarmamr.core

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
        }.sortedBy { it.second }.take(k)
        if (scored.isEmpty()) return null
        val w = scored.map { 1.0 / max(it.second, 0.5) }
        val sw = w.sum()
        val pos = Vec2(
            scored.indices.sumOf { scored[it].first.x * w[it] } / sw,
            scored.indices.sumOf { scored[it].first.y * w[it] } / sw,
        )
        // Spread of the neighbours is a fair stand-in for uncertainty, floored at 1.5 m.
        val spread = scored.indices.sumOf { w[it] * scored[it].first.pos.dist(pos).pow(2) } / sw
        val v = max(spread, 1.5 * 1.5)
        return Fix(pos, v, 0.0, v, scored.first().second, scored.size, Fix.Source.FINGERPRINT)
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
