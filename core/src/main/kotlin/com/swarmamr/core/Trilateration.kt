package com.swarmamr.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** One RTT range to an access point at a known position. */
data class Range(
    val ap: Vec2,
    val distanceM: Double,
    val sigmaM: Double,          // reported standard deviation, floored by the caller
    val bssid: String = "",
)

/** A position fix with its 2x2 covariance (metres squared). */
data class Fix(
    val pos: Vec2,
    val covXX: Double,
    val covXY: Double,
    val covYY: Double,
    val residualRmsM: Double,
    val used: Int,
    val source: Source,
) {
    enum class Source { RTT, FINGERPRINT, RSSI_CENTROID, QR }

    val sigmaM get() = sqrt(max(covXX, covYY))
}

/**
 * Weighted non-linear least squares (Gauss-Newton) for 2-D trilateration.
 *
 * Indoors, blocked line of sight makes RTT read too LONG, never too short, so large positive
 * residuals are down-weighted with a Huber loss.
 */
object Trilateration {
    const val MIN_APS = 3
    private const val HUBER_K = 1.5      // in units of sigma
    private const val ITERATIONS = 8

    fun solve(ranges: List<Range>, start: Vec2? = null): Fix? {
        if (ranges.size < MIN_APS) return null
        var p = start ?: centroid(ranges)
        var weights = DoubleArray(ranges.size) { 1.0 / (ranges[it].sigmaM * ranges[it].sigmaM) }
        for (iter in 0 until ITERATIONS) {
            // Normal equations: (J^T W J) dp = J^T W r
            var a11 = 0.0; var a12 = 0.0; var a22 = 0.0; var b1 = 0.0; var b2 = 0.0
            ranges.forEachIndexed { i, r ->
                val dx = p.x - r.ap.x
                val dy = p.y - r.ap.y
                val d = max(sqrt(dx * dx + dy * dy), 1e-6)
                val jx = dx / d
                val jy = dy / d
                val res = r.distanceM - d
                val w = weights[i]
                a11 += w * jx * jx; a12 += w * jx * jy; a22 += w * jy * jy
                b1 += w * jx * res; b2 += w * jy * res
            }
            val det = a11 * a22 - a12 * a12
            if (abs(det) < 1e-12) return null            // all APs in a line: no 2-D fix
            val sx = (a22 * b1 - a12 * b2) / det
            val sy = (a11 * b2 - a12 * b1) / det
            p = Vec2(p.x + sx, p.y + sy)
            weights = huberWeights(ranges, p)
            if (sx * sx + sy * sy < 1e-6) break
        }
        return finish(ranges, p, weights)
    }

    private fun huberWeights(ranges: List<Range>, p: Vec2) = DoubleArray(ranges.size) { i ->
        val r = ranges[i]
        val base = 1.0 / (r.sigmaM * r.sigmaM)
        val res = r.distanceM - p.dist(r.ap)       // positive = reads long (likely blocked path)
        val z = abs(res) / r.sigmaM
        if (res > 0 && z > HUBER_K) base * HUBER_K / z else base
    }

    private fun finish(ranges: List<Range>, p: Vec2, weights: DoubleArray): Fix? {
        var a11 = 0.0; var a12 = 0.0; var a22 = 0.0; var ss = 0.0
        ranges.forEachIndexed { i, r ->
            val d = max(p.dist(r.ap), 1e-6)
            val jx = (p.x - r.ap.x) / d
            val jy = (p.y - r.ap.y) / d
            val w = weights[i]
            a11 += w * jx * jx; a12 += w * jx * jy; a22 += w * jy * jy
            val res = r.distanceM - d
            ss += res * res
        }
        val det = a11 * a22 - a12 * a12
        if (abs(det) < 1e-12) return null
        val rms = sqrt(ss / ranges.size)
        // Covariance = (J^T W J)^-1, inflated when residuals are larger than the reported sigmas.
        val meanSigma = ranges.map { it.sigmaM }.average()
        val inflate = max(1.0, (rms / meanSigma).let { it * it })
        return Fix(
            pos = p,
            covXX = inflate * a22 / det,
            covXY = -inflate * a12 / det,
            covYY = inflate * a11 / det,
            residualRmsM = rms,
            used = ranges.size,
            source = Fix.Source.RTT,
        )
    }

    private fun centroid(ranges: List<Range>): Vec2 {
        // Start near the closest APs; a plain centroid can sit on the wrong side of a line of APs.
        val w = ranges.map { 1.0 / max(it.distanceM, 0.5) }
        val sw = w.sum()
        return Vec2(
            ranges.indices.sumOf { ranges[it].ap.x * w[it] } / sw,
            ranges.indices.sumOf { ranges[it].ap.y * w[it] } / sw,
        )
    }
}
