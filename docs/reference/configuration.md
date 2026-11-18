# Configuration

## Server environment variables

Set them in the shell before `docker compose up`, or in `backend/docker-compose.yml`.

| Variable | Default | Meaning |
| --- | --- | --- |
| `SITE` | `demo` | Site id the engine runs for. The site file is `data/sites/<SITE>.json`. |
| `GHOSTS` | `3` | Number of simulated workers at start (0 to 30). Can be changed on the dashboard. |
| `MQTT_HOST` | `localhost` (`mosquitto` in Compose) | Broker host. |
| `MQTT_PORT` | `1883` | Broker port. |
| `MQTT_ENABLED` | `1` | `0` runs the HTTP server without MQTT (tests). |
| `DATA_DIR` | `./data` (`/data` in Docker) | Where site files and accuracy samples are stored. |
| `PORT` | `8000` | HTTP port. |

Example:

```bash
SITE=chennai-b GHOSTS=5 docker compose up --build
```

## Android app settings

**Settings** (gear icon, top right):

| Setting | Default | Meaning |
| --- | --- | --- |
| Server address | `http://192.168.1.10:8000` | `http://<server IP>:8000` |
| Message broker (MQTT) | `tcp://192.168.1.10:1883` | `tcp://<server IP>:1883` |
| Site id | `demo` | Must match the server's `SITE` for the traffic demo. |
| This phone's name | generated | Device id on MQTT and in CSV files. Keep it short and unique. |
| Use Wi-Fi RTT | on | Range to RTT access points when the phone supports it (every 500 ms). |
| Scan Wi-Fi every | 5 s | Wi-Fi scan interval. Needs scan throttling off. |
| Ignore phone hotspots | on | Drops random (locally administered) BSSIDs. |
| Office access points only | empty | BSSID prefixes and Wi-Fi names to keep. |
| Sound / Vibration | on | Chime and vibration pattern for each cue. |

## Engine constants

In `backend/swarm_server/engine.py`. Change them only with a simulation run to back the change.

| Constant | Value | Meaning |
| --- | --- | --- |
| `WINDOW` | 10 s | Act on clashes this close. |
| `HORIZON` | 30 s | Prediction horizon. |
| `MARGIN` | 1 s | Safety margin around each booking. |
| `NUDGE_MIN` | 2 s | Delays up to this need no cue. |
| `SLOW_MAX` | 4 s | Delays up to this get a Warning. |
| `HOLD_MAX` | 15 s | Longest hold. |
| `REROUTE_GAIN` | 5 s | Reroute / resequence must beat holding by this much. |
| `MIN_HOLD` | 8 s | Minimum time a cue stays. |
| `RATE_LIMIT` | 15 s | No new Warning within this time of the last cue. |
| `IGNORE_AFTER` | 5 s | Cue not followed after this → plan around the worker. |
