package com.swarmamr.core

import kotlin.math.max
import kotlin.math.sqrt

/** One Wi-Fi RTT measurement as Android reports it, already converted to metres. */
data class RttMeasurement(
    val bssid: String,
    val distanceM: Double,
    val stdDevM: Double,
    val rssi: Int,
    val successes: Int,
    val attempts: Int,
)

/** The fused position the rest of the app uses. */
data class Estimate(
    val timeMs: Long,
    val raw: Vec2,              // filter output before map snapping
    val snapped: Snap,
    val sigmaM: Double,
    val speed: Double,
    val source: Fix.Source,
    val apsUsed: Int,
    val lastFix: Fix?,
)

/**
 * Fuses RTT, RSSI and QR checkpoints into one smoothed, map-matched position.
 * Pure logic: the Android layer feeds it measurements and a clock.
 */
class PositionFuser(
    initialSite: Site,
    private val rttSigmaFloorM: Double = 0.5,
    private val rttMaxStdDevM: Double = 2.0,
    private val rttMinSuccesses: Int = 3,
    private val rssiOnlyAfterMs: Long = 3_000,     // use RSSI only when RTT has been silent this long
) {
    @Volatile var site: Site = initialSite
        private set
    private val kf = Kalman2D()
    private var matcher = MapMatcher(site.grid)
    private var lastPredictMs = 0L
    private var lastRttFixMs = Long.MIN_VALUE / 2
    private var lastSource = Fix.Source.RSSI_CENTROID
    private var lastApsUsed = 0
    private var lastFix: Fix? = null

    fun setSite(s: Site) {
        site = s
        matcher = MapMatcher(s.grid)
    }

    fun setStationary(still: Boolean) = kf.setStationary(still)

    /** Advance the filter clock. Call often (e.g. every 100 ms). */
    fun tick(nowMs: Long) {
        if (lastPredictMs != 0L) kf.predict((nowMs - lastPredictMs) / 1000.0)
        lastPredictMs = nowMs
    }

    /** Returns the RTT fix if one could be computed (also for diagnostics). */
    fun onRtt(results: List<RttMeasurement>, nowMs: Long): Fix? {
        tick(nowMs)
        val ranges = results.mapNotNull { m ->
            val ap = site.ap(m.bssid) ?: return@mapNotNull null
            if (m.successes < rttMinSuccesses || m.stdDevM > rttMaxStdDevM) return@mapNotNull null
            Range(ap.pos, max(0.0, m.distanceM - ap.rttBiasM), max(m.stdDevM, rttSigmaFloorM), m.bssid)
        }
        val fix = Trilateration.solve(ranges, if (kf.initialised) kf.position else null) ?: return null
        lastFix = fix
        if (kf.update(fix)) {
            lastRttFixMs = nowMs
            lastSource = Fix.Source.RTT
            lastApsUsed = fix.used
        }
        return fix
    }

    fun onScan(rssi: Map<String, Double>, nowMs: Long): Fix? {
        tick(nowMs)
        val fix = RssiPositioning.fingerprint(rssi, site.fingerprints)
            ?: RssiPositioning.weightedCentroid(rssi, site)
            ?: return null
        if (nowMs - lastRttFixMs < rssiOnlyAfterMs) return fix   // RTT is working; RSSI only for logs
        lastFix = fix
        if (kf.update(fix)) {
            lastSource = fix.source
            lastApsUsed = fix.used
        }
        return fix
    }

    /** QR checkpoint at a surveyed marker: hard reset of the position. */
    fun onQr(markerId: String, nowMs: Long): Boolean {
        val m = site.marker(markerId) ?: return false
        tick(nowMs)
        kf.reset(m.pos, 0.3 * 0.3)
        matcher.force(m.pos)
        lastSource = Fix.Source.QR
        lastApsUsed = 0
        return true
    }

    fun estimate(nowMs: Long): Estimate? {
        if (!kf.initialised) return null
        val raw = kf.position
        return Estimate(
            timeMs = nowMs,
            raw = raw,
            snapped = matcher.snap(raw),
            sigmaM = sqrt(max(kf.covXX, kf.covYY)),
            speed = kf.speed,
            source = lastSource,
            apsUsed = lastApsUsed,
            lastFix = lastFix,
        )
    }
}
