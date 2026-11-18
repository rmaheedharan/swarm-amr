# MQTT protocol

All topics are under `swarm/v1/<site id>/`. Payloads are UTF-8 JSON. Unknown fields are
ignored on both sides, so new optional fields are backward compatible.

Source of truth: `core/src/main/kotlin/com/swarmamr/core/Protocol.kt`, mirrored in
`backend/swarm_server/protocol.py`. **Change both in the same pull request.**

## Topics

| Topic | Direction | QoS | Retained | Rate |
| --- | --- | --- | --- | --- |
| `dev/<id>/pose` | device → server | 0 | no | 2 Hz |
| `dev/<id>/status` | device → server | 1 | yes (also the Last Will) | on connect / disconnect |
| `dev/<id>/directive` | server → device | 1 | no | on change, re-sent every 4 s while active |
| `engine/heartbeat` | server → all | 0 | yes | 1 Hz |

Example full topic: `swarm/v1/demo/dev/tc57-ravi/pose`.

## Payloads

### pose

```json
{
  "dev": "tc57-ravi",
  "ts": 1731900000123,
  "x": 10.5, "y": 6.0,
  "sigma": 1.4,
  "speed": 0.9,
  "source": "FINGERPRINT",
  "aisle": "A3",
  "picking": false,
  "targets": [{"x": 10.5, "y": 8.4}, {"x": 16.5, "y": 3.6}],
  "battery": 81
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `ts` | int | Device clock, ms since epoch. |
| `x`, `y` | float | Position in the site frame, metres. |
| `sigma` | float | 1-sigma position error, metres. |
| `speed` | float | m/s, from the Kalman filter. |
| `source` | string | `RTT`, `FINGERPRINT`, `RSSI_CENTROID` or `QR`. |
| `aisle` | string | `A<n>` or `cross` (or a zone on open floors). |
| `picking` | bool | The worker is standing at a pick. |
| `targets` | list | Next pick positions, so the server can predict the route. |
| `battery` | int | Percent, −1 if unknown. |

### status

```json
{"dev": "tc57-ravi", "online": true, "ts": 1731900000000, "app": "android", "mode": "swarm"}
```

Set as the MQTT **Last Will** with `"online": false`, so the broker announces a lost device.

### directive

```json
{
  "dev": "tc57-ravi", "seq": 42, "ts": 1731900001000,
  "state": "hold", "text": "Wait 6 s", "aisle": 3,
  "holdS": 6, "expiresMs": 1731900009000
}
```

| Field | Meaning |
| --- | --- |
| `seq` | Increases per device. The phone ignores older sequence numbers. |
| `state` | `normal`, `warning`, `hold`, `go`, `reroute`, `resequence`. |
| `text` | Optional banner text; the phone has defaults. |
| `aisle` | The aisle the cue is about. |
| `holdS` | Seconds to wait (for `hold`). |
| `expiresMs` | Server time after which the phone drops the cue. 0 = no expiry. |
| `swapTo` | For `resequence`: index of the later pick to do first. |

### heartbeat

```json
{"ts": 1731900001000, "siteVersion": 4, "workers": 2, "ghosts": 3}
```

If no heartbeat arrives for 5 s, the phone shows grey **No traffic info**.

## Encoding rules

Both sides write default values and leave out nulls (`WireJson` in Kotlin,
`model_dump_json(exclude_none=True)` in Python).
