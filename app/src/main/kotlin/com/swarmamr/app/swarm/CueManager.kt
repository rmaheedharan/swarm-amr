package com.swarmamr.app.swarm

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.swarmamr.core.CueState
import com.swarmamr.core.DirectiveMsg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the banner shows. OFFLINE is local only: no engine heartbeat, or the cue expired. */
enum class UiCue { NORMAL, WARNING, HOLD, GO, REROUTE, RESEQUENCE, OFFLINE }

data class CueUi(
    val cue: UiCue = UiCue.NORMAL,
    val text: String = "",
    val sinceMs: Long = 0,
    val holdUntilMs: Long = 0,        // local clock, for the countdown
    val expiresAtMs: Long = 0,        // local clock
    val aisle: Int? = null,
    val swapTo: Int? = null,
    val seq: Long = 0,
)

/**
 * Turns directives into banner state, vibration and sound (blueprint section 4).
 * Rules: every state change has its own pattern; a stale cue is dropped after its expiry so a
 * lost connection can never leave a worker stuck on "Hold".
 */
class CueManager(ctx: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        }
    private val tones: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()
    private val _ui = MutableStateFlow(CueUi())
    val ui: StateFlow<CueUi> = _ui.asStateFlow()

    var vibrationOn = true
    var soundOn = true
    var cuesFelt = 0; private set
    private var lastHeartbeatMs = 0L

    fun onHeartbeat(receivedAtMs: Long) {
        lastHeartbeatMs = receivedAtMs
        if (_ui.value.cue == UiCue.OFFLINE) set(CueUi(UiCue.NORMAL, sinceMs = receivedAtMs), feel = false)
    }

    fun onDirective(d: DirectiveMsg, receivedAtMs: Long) {
        val cur = _ui.value
        if (d.seq != 0L && d.seq < cur.seq) return            // out of order
        val ttl = if (d.expiresMs > d.ts) d.expiresMs - d.ts else 5_000
        val cue = when (d.state) {
            CueState.NORMAL -> UiCue.NORMAL
            CueState.WARNING -> UiCue.WARNING
            CueState.HOLD -> UiCue.HOLD
            CueState.GO -> UiCue.GO
            CueState.REROUTE -> UiCue.REROUTE
            CueState.RESEQUENCE -> UiCue.RESEQUENCE
        }
        val next = CueUi(
            cue = cue,
            text = d.text.ifBlank { defaultText(cue, d.holdS, d.aisle) },
            sinceMs = if (cue == cur.cue) cur.sinceMs else receivedAtMs,
            holdUntilMs = if (d.holdS > 0) receivedAtMs + d.holdS * 1000L else 0,
            expiresAtMs = receivedAtMs + ttl,
            aisle = d.aisle,
            swapTo = d.swapTo,
            seq = d.seq,
        )
        set(next, feel = cue != cur.cue)
    }

    /** Call about once a second. Drops expired cues and detects a silent engine. */
    fun tick(nowMs: Long, swarmActive: Boolean) {
        val cur = _ui.value
        if (!swarmActive) {
            if (cur.cue != UiCue.NORMAL) set(CueUi(UiCue.NORMAL, sinceMs = nowMs), feel = false)
            return
        }
        if (nowMs - lastHeartbeatMs > OFFLINE_AFTER_MS) {
            if (cur.cue != UiCue.OFFLINE) set(CueUi(UiCue.OFFLINE, "Swarm off", nowMs), feel = true)
            return
        }
        if (cur.cue == UiCue.GO && nowMs - cur.sinceMs > 2_000) {
            set(cur.copy(cue = UiCue.NORMAL, text = ""), feel = false)
        } else if (cur.cue !in setOf(UiCue.NORMAL, UiCue.OFFLINE, UiCue.GO) && cur.expiresAtMs in 1 until nowMs) {
            set(CueUi(UiCue.NORMAL, sinceMs = nowMs, seq = cur.seq), feel = false)
        }
    }

    /** Settings screen: play a cue so workers can learn the patterns. */
    fun practice(cue: UiCue) = feel(cue)

    private fun set(next: CueUi, feel: Boolean) {
        _ui.value = next
        if (feel) {
            feel(next.cue)
            if (next.cue in setOf(UiCue.WARNING, UiCue.HOLD, UiCue.REROUTE, UiCue.RESEQUENCE)) cuesFelt++
        }
    }

    private fun feel(cue: UiCue) {
        val pattern = PATTERNS[cue]
        if (vibrationOn && pattern != null && vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
        val tone = TONES[cue]
        if (soundOn && tone != null) tones?.startTone(tone.first, tone.second)
    }

    private fun defaultText(cue: UiCue, holdS: Int, aisle: Int?) = when (cue) {
        UiCue.WARNING -> "Slow"
        UiCue.HOLD -> if (holdS > 0) "Wait $holdS s" else "Wait"
        UiCue.GO -> "Go"
        UiCue.REROUTE -> "Avoid aisle ${aisle ?: ""}".trim()
        UiCue.RESEQUENCE -> "Next pick changed"
        else -> ""
    }

    fun release() {
        tones?.release()
    }

    companion object {
        const val OFFLINE_AFTER_MS = 5_000L

        // Off/on timings in ms, starting with a 0 ms delay (blueprint section 4.2).
        val PATTERNS = mapOf(
            UiCue.WARNING to longArrayOf(0, 80, 120, 80),
            UiCue.HOLD to longArrayOf(0, 500),
            UiCue.GO to longArrayOf(0, 150),
            UiCue.REROUTE to longArrayOf(0, 80, 80, 80, 80, 80),
            UiCue.RESEQUENCE to longArrayOf(0, 80, 80, 80, 80, 80),
            UiCue.OFFLINE to longArrayOf(0, 60),
        )

        // Tone and duration in ms.
        val TONES = mapOf(
            UiCue.WARNING to (ToneGenerator.TONE_PROP_BEEP to 150),
            UiCue.HOLD to (ToneGenerator.TONE_PROP_NACK to 400),
            UiCue.GO to (ToneGenerator.TONE_PROP_ACK to 300),
            UiCue.REROUTE to (ToneGenerator.TONE_PROP_BEEP2 to 400),
            UiCue.RESEQUENCE to (ToneGenerator.TONE_PROP_BEEP2 to 400),
        )
    }
}
