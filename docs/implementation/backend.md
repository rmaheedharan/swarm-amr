# Backend

The backend is three services started by one Compose file:

```bash
cd backend
docker compose up --build
```

| Service | Image | Port | Role |
| --- | --- | --- | --- |
| `mosquitto` | `eclipse-mosquitto:2` | 1883 | MQTT broker for phones and server |
| `swarm` | built from `backend/Dockerfile` (Python 3.12-slim) | 8000 | Engine loop, HTTP API, dashboard |
| volume `./data` | | | Site files and accuracy samples survive restarts |

## Code

```text
backend/swarm_server/
├── main.py          FastAPI app, settings from env vars, 1 Hz engine loop
├── mqtt_bridge.py   paho-mqtt client: subscribes to device topics, publishes outgoing
├── coordinator.py   Joins live phones, ghosts and the engine. No network code: easy to test
├── engine.py        Density Matrix Engine (time-window reservations)
├── warehouse.py     Grid graph, shortest paths, aisle lookup
├── workers.py       LiveWorker (a phone) and GhostWorker (simulated)
├── site.py          Site file model + AP filter (mirror of core Site.kt)
├── protocol.py      MQTT payloads (mirror of core Protocol.kt)
├── store.py         Site and accuracy sample storage, summary, CSV
├── labels.py        Printable QR label sheet for markers
└── static/index.html  Dashboard
```

## The engine loop

```python
async def engine_loop():
    while True:
        started = time.time()
        try:
            out = coordinator.tick(started)     # engine tick + heartbeat + changed cues
            bridge.publish(out)
        except Exception:
            log.exception("engine tick failed")  # keep the loop alive whatever happens
        await asyncio.sleep(max(0.05, 1.0 - (time.time() - started)))
```

The `Coordinator`:

1. Turns incoming `pose` messages into `LiveWorker`s (position snapped to the grid, pick
   targets from the phone) and `status` messages into online/offline.
2. Steps the ghosts.
3. Calls `Engine.tick()` and converts each changed directive into a `directive` message with
   a sequence number and expiry. Active cues are re-sent every 4 s; a hold that ends sends **Go**.
4. Publishes the retained heartbeat with the site version and worker counts.

Because the coordinator has no network code, `tests/test_server.py` drives it directly with
fake messages and a fake clock.

## Dashboard

`http://<server IP>:8000` shows the live floor with phones and ghosts, the broker state,
counters of cues sent, a log of recent cues, and the accuracy test summary with a CSV
download. The ghost count can be changed from the top right.

`http://<server IP>:8000/labels/<site id>` prints QR labels for all markers.

## Configuration

See [Configuration](../reference/configuration.md) for environment variables and
[HTTP API](../reference/http-api.md) for endpoints.

## Production notes

The demo broker allows anonymous connections on all interfaces. Before a pilot that lasts
more than a day:

- Add a Mosquitto password file and `allow_anonymous false`.
- Enable TLS on 8883, or keep the broker on an isolated VLAN.
- Put the HTTP API behind a reverse proxy with authentication.
