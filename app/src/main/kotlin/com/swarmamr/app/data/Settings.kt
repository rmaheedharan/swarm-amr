package com.swarmamr.app.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** User settings, stored in SharedPreferences and exposed as a StateFlow. */
data class AppSettings(
    val deviceId: String,
    val brokerUrl: String = "tcp://192.168.1.10:1883",
    val backendUrl: String = "http://192.168.1.10:8000",
    val siteId: String = "demo",
    val useRtt: Boolean = true,
    val scanIntervalS: Int = 5,
    val rttIntervalMs: Int = 500,
    val soundOn: Boolean = true,
    val vibrationOn: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current get() = _state.value

    private fun load(): AppSettings {
        var id = prefs.getString("deviceId", null)
        if (id == null) {
            id = "${Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "").take(10).lowercase()}-${Random.nextInt(1000, 9999)}"
            prefs.edit().putString("deviceId", id).apply()
        }
        val d = AppSettings(deviceId = id)
        return AppSettings(
            deviceId = id,
            brokerUrl = prefs.getString("brokerUrl", d.brokerUrl)!!,
            backendUrl = prefs.getString("backendUrl", d.backendUrl)!!,
            siteId = prefs.getString("siteId", d.siteId)!!,
            useRtt = prefs.getBoolean("useRtt", d.useRtt),
            scanIntervalS = prefs.getInt("scanIntervalS", d.scanIntervalS),
            rttIntervalMs = prefs.getInt("rttIntervalMs", d.rttIntervalMs),
            soundOn = prefs.getBoolean("soundOn", d.soundOn),
            vibrationOn = prefs.getBoolean("vibrationOn", d.vibrationOn),
        )
    }

    fun update(f: (AppSettings) -> AppSettings) {
        val s = f(_state.value)
        prefs.edit()
            .putString("deviceId", s.deviceId)
            .putString("brokerUrl", s.brokerUrl.trim())
            .putString("backendUrl", s.backendUrl.trim().trimEnd('/'))
            .putString("siteId", s.siteId.trim())
            .putBoolean("useRtt", s.useRtt)
            .putInt("scanIntervalS", s.scanIntervalS)
            .putInt("rttIntervalMs", s.rttIntervalMs)
            .putBoolean("soundOn", s.soundOn)
            .putBoolean("vibrationOn", s.vibrationOn)
            .apply()
        _state.value = s
    }
}
