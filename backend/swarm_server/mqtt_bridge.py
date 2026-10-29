"""Connects the coordinator to the Mosquitto broker."""
import logging
import time

import paho.mqtt.client as mqtt

from . import protocol as P

log = logging.getLogger("swarm.mqtt")


class MqttBridge:
    def __init__(self, coordinator, host: str, port: int = 1883, client_id: str = "swarm-server"):
        self.co = coordinator
        self.host, self.port = host, port
        self.connected = False
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id, clean_session=True)
        self.client.reconnect_delay_set(1, 10)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message

    def start(self):
        self.client.connect_async(self.host, self.port, keepalive=10)
        self.client.loop_start()

    def stop(self):
        self.client.loop_stop()
        self.client.disconnect()

    def subscribe_site(self):
        root = P.root(self.co.site.id)
        self.client.subscribe([(f"{root}/dev/+/pose", 0), (f"{root}/dev/+/status", 1)])

    def publish(self, out):
        for m in out:
            self.client.publish(m.topic, m.payload, qos=m.qos, retain=m.retain)

    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        if reason_code.is_failure:
            log.warning("MQTT connect refused: %s", reason_code)
            return
        self.connected = True
        log.info("MQTT connected to %s:%s", self.host, self.port)
        self.subscribe_site()

    def _on_disconnect(self, client, userdata, flags, reason_code, properties=None):
        self.connected = False
        log.warning("MQTT disconnected: %s", reason_code)

    def _on_message(self, client, userdata, msg):
        self.co.on_message(msg.topic, msg.payload, time.time())
