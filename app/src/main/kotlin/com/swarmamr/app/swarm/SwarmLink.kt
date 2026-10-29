package com.swarmamr.app.swarm

import android.util.Log
import com.swarmamr.core.DirectiveMsg
import com.swarmamr.core.HeartbeatMsg
import com.swarmamr.core.PoseMsg
import com.swarmamr.core.StatusMsg
import com.swarmamr.core.Topics
import com.swarmamr.core.WireJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

enum class LinkState { OFF, CONNECTING, CONNECTED, RECONNECTING, FAILED }

/**
 * MQTT link to the swarm server. Paho reconnects on its own (1 s up to 128 s backoff);
 * the session is persistent so the directive subscription survives a drop.
 */
class SwarmLink(
    private val onDirective: (DirectiveMsg, receivedAtMs: Long) -> Unit,
    private val onHeartbeat: (HeartbeatMsg, receivedAtMs: Long) -> Unit,
) {
    private var client: MqttAsyncClient? = null
    private var site = ""
    private var dev = ""
    private val _state = MutableStateFlow(LinkState.OFF)
    val state: StateFlow<LinkState> = _state.asStateFlow()
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    fun connect(brokerUrl: String, siteId: String, deviceId: String) {
        disconnect()
        site = siteId; dev = deviceId
        _state.value = LinkState.CONNECTING
        val c = runCatching { MqttAsyncClient(brokerUrl, "swarm-$deviceId", MemoryPersistence()) }
            .getOrElse {
                _error.value = "Bad broker address: ${it.message}"
                _state.value = LinkState.FAILED
                return
            }
        client = c
        c.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                _state.value = LinkState.CONNECTED
                _error.value = ""
                subscribe(c)
                publishStatus(true)
            }

            override fun connectionLost(cause: Throwable?) {
                _state.value = LinkState.RECONNECTING
                _error.value = cause?.message ?: "connection lost"
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                val now = System.currentTimeMillis()
                val text = String(message.payload)
                runCatching {
                    when (topic) {
                        Topics.directive(site, dev) -> onDirective(WireJson.decodeFromString(DirectiveMsg.serializer(), text), now)
                        Topics.heartbeat(site) -> onHeartbeat(WireJson.decodeFromString(HeartbeatMsg.serializer(), text), now)
                    }
                }.onFailure { Log.w(TAG, "bad message on $topic", it) }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = false
            keepAliveInterval = 10
            connectionTimeout = 5
            maxInflight = 50
            setWill(
                Topics.status(site, dev),
                WireJson.encodeToString(StatusMsg.serializer(), StatusMsg(dev, false)).toByteArray(),
                1, true,
            )
        }
        runCatching {
            c.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) = Unit
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    _error.value = exception?.message ?: "could not connect"
                    _state.value = LinkState.FAILED
                }
            })
        }.onFailure {
            _error.value = it.message ?: "could not connect"
            _state.value = LinkState.FAILED
        }
    }

    private fun subscribe(c: MqttAsyncClient) {
        runCatching {
            c.subscribe(arrayOf(Topics.directive(site, dev), Topics.heartbeat(site)), intArrayOf(1, 0))
        }.onFailure { Log.w(TAG, "subscribe failed", it) }
    }

    fun publishPose(p: PoseMsg) = publish(Topics.pose(site, dev), WireJson.encodeToString(PoseMsg.serializer(), p), 0, false)

    fun publishStatus(online: Boolean, mode: String = "swarm") = publish(
        Topics.status(site, dev),
        WireJson.encodeToString(StatusMsg.serializer(), StatusMsg(dev, online, System.currentTimeMillis(), "android", mode)),
        1, true,
    )

    private fun publish(topic: String, json: String, qos: Int, retained: Boolean) {
        val c = client ?: return
        if (!c.isConnected) return                      // poses are not queued: old positions are useless
        runCatching { c.publish(topic, json.toByteArray(), qos, retained) }
            .onFailure { Log.w(TAG, "publish failed", it) }
    }

    fun disconnect() {
        val c = client ?: return
        client = null
        runCatching {
            if (c.isConnected) {
                publishStatusOn(c, false)
                c.disconnect(500).waitForCompletion(1000)
            }
            c.close()
        }
        _state.value = LinkState.OFF
    }

    private fun publishStatusOn(c: MqttAsyncClient, online: Boolean) {
        val json = WireJson.encodeToString(StatusMsg.serializer(), StatusMsg(dev, online, System.currentTimeMillis(), "android"))
        runCatching { c.publish(Topics.status(site, dev), json.toByteArray(), 1, true).waitForCompletion(500) }
    }

    companion object { private const val TAG = "SwarmLink" }
}
