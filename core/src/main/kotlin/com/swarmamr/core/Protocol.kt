package com.swarmamr.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * MQTT message format. Mirrored in backend/swarm_server/protocol.py; change both together.
 *
 * Topics, all under swarm/v1/{site}/:
 *   dev/{id}/pose       device -> server  QoS 0, 2 Hz
 *   dev/{id}/status     device -> server  QoS 1, retained, also the Last Will ("online": false)
 *   dev/{id}/directive  server -> device  QoS 1
 *   engine/heartbeat    server -> all     QoS 0, retained, 1 Hz
 */
object Topics {
    fun root(site: String) = "swarm/v1/$site"
    fun pose(site: String, dev: String) = "${root(site)}/dev/$dev/pose"
    fun status(site: String, dev: String) = "${root(site)}/dev/$dev/status"
    fun directive(site: String, dev: String) = "${root(site)}/dev/$dev/directive"
    fun heartbeat(site: String) = "${root(site)}/engine/heartbeat"
}

@Serializable
data class Point(val x: Double, val y: Double)

@Serializable
data class PoseMsg(
    val dev: String,
    val ts: Long,                       // device clock, ms since epoch (NTP synced)
    val x: Double,
    val y: Double,
    val sigma: Double,                  // 1-sigma position error estimate, m
    val speed: Double,                  // m/s
    val source: String,                 // RTT, FINGERPRINT, RSSI_CENTROID, QR
    val aisle: String,                  // "A3" or "cross"
    val picking: Boolean = false,       // standing at a pick face
    val targets: List<Point> = emptyList(),   // next pick locations, in order (demo pick list)
    val battery: Int = -1,
)

@Serializable
data class StatusMsg(
    val dev: String,
    val online: Boolean,
    val ts: Long = 0,
    val app: String = "",
    val mode: String = "",              // "swarm", "accuracy", "survey"
)

@Serializable
enum class CueState {
    @SerialName("normal") NORMAL,
    @SerialName("warning") WARNING,
    @SerialName("hold") HOLD,
    @SerialName("go") GO,
    @SerialName("reroute") REROUTE,
    @SerialName("resequence") RESEQUENCE,
}

@Serializable
data class DirectiveMsg(
    val dev: String,
    val seq: Long,
    val ts: Long,                       // server clock, ms
    val state: CueState,
    val text: String = "",              // one or two words for the banner: "Wait 6 s", "Use aisle 4"
    val aisle: Int? = null,
    val holdS: Int = 0,
    val expiresMs: Long = 0,            // drop the cue after this server time (stale = never trap a worker)
    val swapTo: Int? = null,            // resequence: index in `targets` to do first
)

@Serializable
data class HeartbeatMsg(
    val ts: Long,
    val siteVersion: Int = 0,
    val workers: Int = 0,
    val ghosts: Int = 0,
)

val WireJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
