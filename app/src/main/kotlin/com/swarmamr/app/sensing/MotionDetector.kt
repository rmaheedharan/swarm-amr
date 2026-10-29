package com.swarmamr.app.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects "standing still" from accelerometer variance, so the position filter does not
 * drift while a picker stands at a pick face. Needs no runtime permission.
 */
class MotionDetector(ctx: Context, private val onChange: (stationary: Boolean) -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val sensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val window = ArrayDeque<Pair<Long, Double>>()
    private var stationary = false

    fun start() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val t = e.timestamp / 1_000_000                 // ms
        val m = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()
        window.addLast(t to m)
        while (window.isNotEmpty() && t - window.first().first > WINDOW_MS) window.removeFirst()
        if (window.size < 20) return
        val mean = window.sumOf { it.second } / window.size
        val sd = sqrt(window.sumOf { (it.second - mean) * (it.second - mean) } / window.size)
        val still = sd < STILL_SD
        if (still != stationary) {
            stationary = still
            onChange(still)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val WINDOW_MS = 1500L
        const val STILL_SD = 0.25        // m/s^2
    }
}
