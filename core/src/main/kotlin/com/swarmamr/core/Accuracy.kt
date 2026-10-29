package com.swarmamr.core

import kotlinx.serialization.Serializable
import kotlin.math.ceil

/** One accuracy sample: the app's estimate while the tester stood on a surveyed marker. */
@Serializable
data class AccuracySample(
    val timeMs: Long,
    val markerId: String,
    val truthX: Double,
    val truthY: Double,
    val estX: Double,
    val estY: Double,
    val source: String,          // RTT, FINGERPRINT, RSSI_CENTROID, QR, NONE
    val apsUsed: Int,
    val sigmaM: Double,
    val truthAisle: String,      // "A3" or "cross"
    val estAisle: String,
    val device: String = "",
) {
    val errorM get() = Vec2(truthX, truthY).dist(Vec2(estX, estY))
    val aisleCorrect get() = truthAisle == estAisle
}

@Serializable
data class AccuracySummary(
    val samples: Int,
    val markers: Int,
    val meanM: Double,
    val p50M: Double,
    val p90M: Double,
    val maxM: Double,
    val correctAislePct: Double,
    val bySource: Map<String, Int>,
) {
    /** Blueprint gate 1: correct aisle at least 95%, p90 error 2 m or less. */
    val passesGate1 get() = samples > 0 && correctAislePct >= 95.0 && p90M <= 2.0
}

private fun f3(v: Double) = String.format(java.util.Locale.US, "%.3f", v)

object Accuracy {
    fun summarise(samples: List<AccuracySample>): AccuracySummary {
        if (samples.isEmpty()) return AccuracySummary(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyMap())
        val errs = samples.map { it.errorM }.sorted()
        return AccuracySummary(
            samples = samples.size,
            markers = samples.map { it.markerId }.distinct().size,
            meanM = errs.average(),
            p50M = percentile(errs, 50.0),
            p90M = percentile(errs, 90.0),
            maxM = errs.last(),
            correctAislePct = 100.0 * samples.count { it.aisleCorrect } / samples.size,
            bySource = samples.groupingBy { it.source }.eachCount(),
        )
    }

    /** Nearest-rank percentile of a sorted list. */
    fun percentile(sorted: List<Double>, pct: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val rank = ceil(pct / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    const val CSV_HEADER =
        "time_ms,marker,truth_x,truth_y,est_x,est_y,error_m,source,aps_used,sigma_m,truth_aisle,est_aisle,aisle_ok,device"

    fun toCsv(samples: List<AccuracySample>): String = buildString {
        appendLine(CSV_HEADER)
        for (s in samples) {
            appendLine(
                listOf(
                    s.timeMs, s.markerId, f3(s.truthX), f3(s.truthY),
                    f3(s.estX), f3(s.estY), f3(s.errorM),
                    s.source, s.apsUsed, f3(s.sigmaM), s.truthAisle, s.estAisle,
                    if (s.aisleCorrect) 1 else 0, s.device,
                ).joinToString(","),
            )
        }
    }
}
