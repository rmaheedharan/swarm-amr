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
    { "x": 4.5, "y": 3.6, "rssi": { "aa:bb:cc:dd:ee:01": -52.5 }, "std": { "aa:bb:cc:dd:ee:01": 2.1 }, "markerId": "A1-03", "samples": 6 }
  ],
  "naming": { "pattern": "L{AA}{SS}", "firstAisle": 1, "firstSpot": 1, "everyCells": 2 },
  "image": { "file": "plan-1731850000000.png", "widthPx": 2346, "heightPx": 1434, "metresPerPx": 0.021 },
  "apFilter": { "dropRandom": true, "allowPrefixes": ["7c:b6:8d"], "allowSsids": [] }
}
```

| Field | Meaning |
| --- | --- |
| `version` | Goes up by one each time the server saves the file. The heartbeat carries it so phones can see an old map. |
| `grid` | Aisle `a` has its centreline at `x = originX + (2a + 1) * aislePitchM / 2`. Cross-aisles at `y = originY` and `y = originY + (aisleLenCells + 1) * cellM`. |
| `narrowAisles` | Aisle numbers where two people cannot pass. Only these get Slow / Wait / Go cues. |
| `accessPoints` | Only needed for RTT and the centroid fallback. `rttBiasM` is set by **Calibrate RTT** and subtracted from every RTT range. |
| `markers` | Surveyed floor points with QR labels `SWM:<id>`. Ground truth for the accuracy test. |
| `fingerprints` | Mean RSSI (dBm) per BSSID at a point, from a standing recording or a walk (`markerId` `W<n>.<piece>`). `std` is how much each signal moved while recording. Used by kNN positioning. |
| `naming` | How automatic markers are named. |
| `grid.openFloor` | An open floor (no walkways). Positions are not snapped to aisles. |
| `image` | Optional floor plan picture. The picture stays on the phone; this holds its file name, size in pixels and scale. Its bottom-left corner is the grid origin. |
| `apFilter` | Which access points to use (see below). Missing = `dropRandom` true, no allow list. |

### `apFilter`: ignore phone hotspots

The phone app, the Mac app and the backend use the same rule, in this order, for each BSSID
(lower case, colon separated; prefixes compared without case):

1. It starts with one of `allowPrefixes`: **keep**. This wins over `dropRandom`.
2. `dropRandom` is true and the first byte has bit `0x02` set (a locally administered, "random" address, used by most phone and laptop hotspots): **drop**.
3. `allowPrefixes` and `allowSsids` are both empty: **keep**.
4. Its Wi-Fi name (SSID) is known: keep only if it is in `allowSsids`.
5. Its name is not known (a stored fingerprint has none): keep only if `allowPrefixes` is empty.

It is applied to every live scan before recording or matching, and to stored fingerprints when
matching. **Settings > Clean recordings** removes dropped BSSIDs from the saved fingerprints.
Note: some office access points give extra Wi-Fi names a locally administered BSSID
(for example `7e:...` next to `7c:...`); list their prefix in `allowPrefixes` to keep them.

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
