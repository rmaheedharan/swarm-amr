# Site file format

The app, the server and the engine share one JSON site file. The app saves it on the phone,
**Survey > Send to server** stores it at `backend/data/sites/<id>.json`, and
`GET /api/sites/<id>` returns it. Code: `core/.../Site.kt` and `backend/swarm_server/site.py`.

All positions are metres in the site frame: origin at a building corner, x across the aisles,
y along the aisles.

```json
{
  "id": "demo",
  "name": "Chennai DC, zone B",
  "version": 4,
  "grid": {
    "originX": 0.0, "originY": 0.0,
    "aislePitchM": 3.0,
    "cellM": 1.2,
    "nAisles": 8,
    "aisleLenCells": 12,
    "narrowAisles": [1, 2, 5, 6]
  },
  "accessPoints": [
    { "bssid": "aa:bb:cc:dd:ee:01", "x": 4.5, "y": 7.2, "label": "AP dock", "rttBiasM": 0.6, "frequencyMhz": 5180 }
  ],
  "markers": [
    { "id": "A1-03", "x": 4.5, "y": 3.6, "note": "" }
  ],
  "fingerprints": [
    { "x": 4.5, "y": 3.6, "rssi": { "aa:bb:cc:dd:ee:01": -52.5 }, "markerId": "A1-03", "samples": 6 }
  ]
}
```

| Field | Meaning |
| --- | --- |
| `version` | Goes up by one each time the server saves the file. The heartbeat carries it so phones can see an old map. |
| `grid` | Aisle `a` has its centreline at `x = originX + (2a + 1) * aislePitchM / 2`. Cross-aisles at `y = originY` and `y = originY + (aisleLenCells + 1) * cellM`. |
| `narrowAisles` | Aisle numbers where two people cannot pass. Only these get Slow / Wait / Go cues. |
| `accessPoints` | Only needed for RTT and the centroid fallback. `rttBiasM` is set by **Calibrate RTT** and subtracted from every RTT range. |
| `markers` | Surveyed floor points with QR labels `SWM:<id>`. Ground truth for the accuracy test. |
| `fingerprints` | Mean RSSI (dBm) per BSSID at a point, from **Fingerprint 20 s**. Used by kNN positioning. |

## MQTT messages

Topics are under `swarm/v1/<site id>/`. Payloads are JSON; see `core/.../Protocol.kt` and
`backend/swarm_server/protocol.py` (change both together).

| Topic | From | Payload |
| --- | --- | --- |
| `dev/<id>/pose` | phone, 2 per second | `dev, ts, x, y, sigma, speed, source, aisle, picking, targets[], battery` |
| `dev/<id>/status` | phone, retained, also the Last Will | `dev, online, ts, app, mode` |
| `dev/<id>/directive` | server, QoS 1 | `dev, seq, ts, state, text, aisle, holdS, expiresMs, swapTo` |
| `engine/heartbeat` | server, every second, retained | `ts, siteVersion, workers, ghosts` |

`state` is one of `normal, warning, hold, go, reroute, resequence`. A phone drops a cue after
`expiresMs`, and shows "Swarm off" if no heartbeat arrives for 5 s.
