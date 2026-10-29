package com.swarmamr.app

import android.content.Context
import android.os.BatteryManager
import com.swarmamr.app.data.SettingsStore
import com.swarmamr.app.data.SiteRepository
import com.swarmamr.app.sensing.MotionDetector
import com.swarmamr.app.sensing.SeenAp
import com.swarmamr.app.sensing.WifiSensors
import com.swarmamr.app.swarm.CueManager
import com.swarmamr.app.swarm.LinkState
import com.swarmamr.app.swarm.SwarmLink
import com.swarmamr.app.swarm.UiCue
import com.swarmamr.core.DemoPickList
import com.swarmamr.core.Estimate
import com.swarmamr.core.Fix
import com.swarmamr.core.Point
import com.swarmamr.core.PoseMsg
import com.swarmamr.core.PositionFuser
import com.swarmamr.core.RttMeasurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Live sensing numbers for the screens. */
data class WifiStatus(
    val rttSupported: Boolean = false,
    val rttAvailable: Boolean = false,
    val seen: List<SeenAp> = emptyList(),
    val lastScanMs: Long = 0,
    val lastRtt: List<RttMeasurement> = emptyList(),
    val lastRttMs: Long = 0,
    val scansRequested: Int = 0,
    val scansRefused: Int = 0,
    val stationary: Boolean = false,
)

data class PickUi(
    val label: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val picking: Boolean = false,
    val target: com.swarmamr.core.Vec2? = null,
)

/**
 * Everything that runs while positioning is on. Owned by the Application; kept alive by
 * PositioningService (a foreground service) so it keeps running with the screen off.
 */
class Runtime(
    private val ctx: Context,
    val settings: SettingsStore,
    val sites: SiteRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    val fuser = PositionFuser(sites.current)
    val cues = CueManager(ctx)
    val link = SwarmLink(
        onDirective = { d, at ->
            cues.onDirective(d, at)
            d.swapTo?.let { off -> if (d.state == com.swarmamr.core.CueState.RESEQUENCE) synchronized(lock) { picks?.bringForward(off) } }
        },
        onHeartbeat = { _, at -> cues.onHeartbeat(at) },
    )

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _estimate = MutableStateFlow<Estimate?>(null)
    val estimate: StateFlow<Estimate?> = _estimate.asStateFlow()
    private val _wifi = MutableStateFlow(WifiStatus())
    val wifi: StateFlow<WifiStatus> = _wifi.asStateFlow()
    private val _swarm = MutableStateFlow(false)
    val swarmOn: StateFlow<Boolean> = _swarm.asStateFlow()
    private val _pick = MutableStateFlow(PickUi())
    val pick: StateFlow<PickUi> = _pick.asStateFlow()
    val linkState: StateFlow<LinkState> get() = link.state

    /** Latest raw RSSI scan, used by the survey screen to record fingerprints. */
    @Volatile var lastScan: Map<String, Double> = emptyMap(); private set
    private var picks: DemoPickList? = null
    private var wifiSensors: WifiSensors? = null
    private var motion: MotionDetector? = null
    private val jobs = mutableListOf<Job>()

    init {
        scope.launch { sites.site.collect { s -> synchronized(lock) { fuser.setSite(s) } } }
        scope.launch {
            settings.state.collect { s ->
                cues.soundOn = s.soundOn
                cues.vibrationOn = s.vibrationOn
            }
        }
    }

    fun start() {
        if (_running.value) return
        val s = settings.current
        val w = WifiSensors(
            ctx, scope,
            onScan = { rssi, seen, now ->
                lastScan = rssi
                synchronized(lock) { fuser.onScan(rssi, now) }
                _wifi.value = _wifi.value.copy(seen = seen, lastScanMs = now)
            },
            onRtt = { m, now ->
                synchronized(lock) { fuser.onRtt(m, now) }
                _wifi.value = _wifi.value.copy(lastRtt = m, lastRttMs = now)
            },
        )
        wifiSensors = w
        w.start(s.scanIntervalS, s.rttIntervalMs, s.useRtt)
        _wifi.value = _wifi.value.copy(rttSupported = w.rttSupported, rttAvailable = w.rttAvailable)
        motion = MotionDetector(ctx) { still ->
            synchronized(lock) { fuser.setStationary(still) }
            _wifi.value = _wifi.value.copy(stationary = still)
        }.also { it.start() }

        jobs += scope.launch {
            var n = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                synchronized(lock) { fuser.tick(now) }
                if (n % 5 == 0L) halfSecond(now)                      // 2 Hz
                if (n % 10 == 0L) {
                    cues.tick(now, _swarm.value)
                    _wifi.value = _wifi.value.copy(
                        rttAvailable = w.rttAvailable,
                        scansRequested = w.scansRequested,
                        scansRefused = w.scansRefused,
                    )
                }
                n++
                delay(100)
            }
        }
        _running.value = true
    }

    private fun halfSecond(now: Long) {
        val e = synchronized(lock) { fuser.estimate(now) } ?: return
        _estimate.value = e
        if (!_swarm.value) return
        val list = picks ?: return
        val picking = synchronized(lock) { list.update(e.snapped.pos, now) }
        val up = synchronized(lock) { list.upcoming(4) }
        _pick.value = PickUi(list.current?.label ?: "All done", list.done, list.total, picking, list.current?.pos)
        link.publishPose(
            PoseMsg(
                dev = settings.current.deviceId,
                ts = now,
                x = e.snapped.pos.x,
                y = e.snapped.pos.y,
                sigma = e.sigmaM,
                speed = e.speed,
                source = e.source.name,
                aisle = e.snapped.area,
                picking = picking,
                targets = up.map { Point(it.pos.x, it.pos.y) },
                battery = battery(),
            ),
        )
    }

    fun stop() {
        setSwarm(false)
        jobs.forEach { it.cancel() }
        jobs.clear()
        wifiSensors?.stop(); wifiSensors = null
        motion?.stop(); motion = null
        _running.value = false
    }

    fun setSwarm(on: Boolean) {
        if (on == _swarm.value) return
        if (on) {
            val s = settings.current
            synchronized(lock) { picks = DemoPickList(sites.current) }
            link.connect(s.brokerUrl, s.siteId, s.deviceId)
        } else {
            link.disconnect()
            synchronized(lock) { picks = null }
            _pick.value = PickUi()
        }
        _swarm.value = on
    }

    fun nextPick() = synchronized(lock) { picks?.next() }

    fun qrCheckpoint(markerId: String): Boolean = synchronized(lock) {
        fuser.onQr(markerId, System.currentTimeMillis())
    }.also { if (it) _estimate.value = synchronized(lock) { fuser.estimate(System.currentTimeMillis()) } }

    fun practiceCue(c: UiCue) = cues.practice(c)

    val positionSource: Fix.Source? get() = _estimate.value?.source

    private fun battery(): Int =
        (ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
}
