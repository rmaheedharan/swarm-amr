package com.swarmamr.app

import android.content.Context
import android.os.Build
import com.swarmamr.app.data.Http
import com.swarmamr.core.Accuracy
import com.swarmamr.core.AccuracySample
import com.swarmamr.core.AccuracySummary
import com.swarmamr.core.MapMatcher
import com.swarmamr.core.Marker
import com.swarmamr.core.RssiPositioning
import com.swarmamr.core.WireJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** A measurement that runs for a few seconds while the tester stands on a marker. */
data class Busy(val what: String, val markerId: String, val untilMs: Long)

/**
 * Accuracy test and survey tools. Each tool runs while the tester stands still on a
 * surveyed floor marker.
 */
class FieldTools(private val ctx: Context, private val rt: Runtime) {
    private val samplesFile = File(ctx.filesDir, "accuracy.json")
    private val _samples = MutableStateFlow(loadSamples())
    val samples: StateFlow<List<AccuracySample>> = _samples.asStateFlow()
    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy.asStateFlow()

    val summary: AccuracySummary get() = Accuracy.summarise(_samples.value)

    private fun loadSamples(): List<AccuracySample> = runCatching {
        WireJson.decodeFromString(ListSerializer(AccuracySample.serializer()), samplesFile.readText())
    }.getOrDefault(emptyList())

    private fun saveSamples(list: List<AccuracySample>) {
        _samples.value = list
        samplesFile.writeText(WireJson.encodeToString(ListSerializer(AccuracySample.serializer()), list))
    }

    /** Record the estimate twice a second for [seconds] while standing on [m]. */
    suspend fun recordAccuracy(m: Marker, seconds: Int = 10): Int {
        val matcher = MapMatcher(rt.sites.current.grid)
        val truthArea = matcher.snapRaw(m.pos).area
        val end = System.currentTimeMillis() + seconds * 1000L
        _busy.value = Busy("Measuring accuracy", m.id, end)
        val got = mutableListOf<AccuracySample>()
        try {
            while (System.currentTimeMillis() < end) {
                delay(500)
                val e = rt.estimate.value ?: continue
                if (System.currentTimeMillis() - e.timeMs > 2_000) continue       // stale
                got += AccuracySample(
                    timeMs = e.timeMs,
                    markerId = m.id,
                    truthX = m.x, truthY = m.y,
                    estX = e.snapped.pos.x, estY = e.snapped.pos.y,
                    source = e.source.name,
                    apsUsed = e.apsUsed,
                    sigmaM = e.sigmaM,
                    truthAisle = truthArea,
                    estAisle = e.snapped.area,
                    device = Build.MODEL,
                )
            }
        } finally {
            _busy.value = null
        }
        saveSamples(_samples.value + got)
        return got.size
    }

    fun clearSamples() = saveSamples(emptyList())

    /** Stand on a marker and average Wi-Fi scans into a fingerprint. */
    suspend fun recordFingerprint(m: Marker, seconds: Int = 20): Int {
        val end = System.currentTimeMillis() + seconds * 1000L
        _busy.value = Busy("Recording Wi-Fi fingerprint", m.id, end)
        val scans = mutableListOf<Map<String, Double>>()
        var last: Map<String, Double>? = null
        try {
            while (System.currentTimeMillis() < end) {
                val s = rt.lastScan
                if (s.isNotEmpty() && s !== last) { scans += s; last = s }
                delay(250)
            }
        } finally {
            _busy.value = null
        }
        if (scans.isEmpty()) return 0
        rt.sites.addFingerprint(RssiPositioning.average(scans, m.pos, m.id))
        return scans.size
    }

    /**
     * RTT calibration: stand on a marker, compare measured ranges with true distances, and
     * store the mean difference per AP as its bias.
     */
    suspend fun calibrateRtt(m: Marker, seconds: Int = 15): Map<String, Double> {
        val end = System.currentTimeMillis() + seconds * 1000L
        _busy.value = Busy("Calibrating RTT", m.id, end)
        val diffs = mutableMapOf<String, MutableList<Double>>()
        var lastMs = 0L
        try {
            while (System.currentTimeMillis() < end) {
                val w = rt.wifi.value
                if (w.lastRttMs != lastMs) {
                    lastMs = w.lastRttMs
                    for (r in w.lastRtt) {
                        val ap = rt.sites.current.ap(r.bssid) ?: continue
                        diffs.getOrPut(ap.bssid) { mutableListOf() } += r.distanceM - ap.pos.dist(m.pos)
                    }
                }
                delay(200)
            }
        } finally {
            _busy.value = null
        }
        val bias = diffs.filterValues { it.size >= 5 }.mapValues { (_, v) -> v.sorted()[v.size / 2] }   // median
        if (bias.isNotEmpty()) rt.sites.setBiases(bias)
        return bias
    }

    /** CSV in the app cache, ready to share. */
    fun exportCsv(): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        return File(dir, "accuracy-${System.currentTimeMillis()}.csv").apply { writeText(Accuracy.toCsv(_samples.value)) }
    }

    fun exportSite(): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        return File(dir, "site-${rt.sites.current.id}.json").apply { writeText(rt.sites.current.toJson()) }
    }

    /** POST {backend}/api/accuracy with all samples. */
    suspend fun uploadSamples(backendUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Http.send(
                "POST", "$backendUrl/api/accuracy",
                WireJson.encodeToString(ListSerializer(AccuracySample.serializer()), _samples.value),
            )
        }
    }
}
