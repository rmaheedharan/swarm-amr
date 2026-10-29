package com.swarmamr.app.sensing

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.swarmamr.core.RttMeasurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** An access point seen in the last Wi-Fi scan (for the Survey screen). */
data class SeenAp(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val rttResponder: Boolean,
)

/**
 * Wi-Fi scanning (RSSI) and Wi-Fi RTT ranging.
 *
 * Android limits apps to 4 Wi-Fi scans every 2 minutes unless "Wi-Fi scan throttling" is
 * turned off in Developer options. RTT ranging itself is not throttled; it only needs the
 * ScanResult of each responder once.
 */
class WifiSensors(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onScan: (rssi: Map<String, Double>, seen: List<SeenAp>, nowMs: Long) -> Unit,
    private val onRtt: (List<RttMeasurement>, nowMs: Long) -> Unit,
) {
    private val wifi = ctx.applicationContext.getSystemService(WifiManager::class.java)
    private val rtt: WifiRttManager? =
        if (ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            ctx.getSystemService(WifiRttManager::class.java)
        } else null
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var responders: List<ScanResult> = emptyList()
    private val jobs = mutableListOf<Job>()
    private var registered = false

    val rttSupported get() = rtt != null
    val rttAvailable get() = rtt?.isAvailable == true
    var scansRequested = 0; private set
    var scansRefused = 0; private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = readScan()
    }

    fun start(scanIntervalS: Int, rttIntervalMs: Int, useRtt: Boolean) {
        stop()
        ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        jobs += scope.launch {
            while (isActive) {
                requestScan()
                delay(scanIntervalS * 1000L)
            }
        }
        if (useRtt && rtt != null) {
            jobs += scope.launch {
                while (isActive) {
                    rangeOnce()
                    delay(rttIntervalMs.toLong())
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        if (registered) {
            runCatching { ctx.unregisterReceiver(receiver) }
            registered = false
        }
    }

    @Suppress("DEPRECATION")
    private fun requestScan() {
        scansRequested++
        if (!wifi.startScan()) scansRefused++      // throttled: we still read the cached results
        readScan()
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun readScan() {
        if (!hasLocation()) return
        val nowUs = SystemClock.elapsedRealtime() * 1000
        val results = runCatching { wifi.scanResults }.getOrDefault(emptyList())
            .filter { nowUs - it.timestamp < 20_000_000 }          // ignore results older than 20 s
        responders = results.filter { it.is80211mcResponder }
        val seen = results.map {
            SeenAp(
                bssid = it.BSSID.lowercase(),
                ssid = ssidOf(it),
                rssi = it.level,
                frequencyMhz = it.frequency,
                rttResponder = it.is80211mcResponder,
            )
        }.sortedByDescending { it.rssi }
        onScan(seen.associate { it.bssid to it.rssi.toDouble() }, seen, System.currentTimeMillis())
    }

    @Suppress("DEPRECATION")
    private fun ssidOf(r: ScanResult): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            r.wifiSsid?.toString()?.trim('"') ?: ""
        } else {
            r.SSID ?: ""
        }

    @SuppressLint("MissingPermission")
    private suspend fun rangeOnce() {
        val manager = rtt ?: return
        val targets = responders
        if (targets.isEmpty() || !manager.isAvailable || !hasLocation()) return
        val request = RangingRequest.Builder()
            .addAccessPoints(targets.take(RangingRequest.getMaxPeers()))
            .build()
        val results = withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    manager.startRanging(request, executor, object : RangingResultCallback() {
                        override fun onRangingFailure(code: Int) {
                            if (cont.isActive) cont.resume(emptyList<RangingResult>())
                        }

                        override fun onRangingResults(results: List<RangingResult>) {
                            if (cont.isActive) cont.resume(results)
                        }
                    })
                }.onFailure { if (cont.isActive) cont.resume(emptyList()) }
            }
        } ?: return
        val measurements = results.mapNotNull { r ->
            if (r.status != RangingResult.STATUS_SUCCESS) return@mapNotNull null
            val mac = r.macAddress?.toString()?.lowercase() ?: return@mapNotNull null
            RttMeasurement(
                bssid = mac,
                distanceM = r.distanceMm / 1000.0,
                stdDevM = r.distanceStdDevMm / 1000.0,
                rssi = r.rssi,
                successes = r.numSuccessfulMeasurements,
                attempts = r.numAttemptedMeasurements,
            )
        }
        if (measurements.isNotEmpty()) onRtt(measurements, System.currentTimeMillis())
    }
}
