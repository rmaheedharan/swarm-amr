# System architecture

## Components

```mermaid
flowchart TB
    subgraph Device["Picker device (Android 9+, Zebra TC5x)"]
        WS["WifiSensors<br/>scan + RTT ranging"]
        MD["MotionDetector<br/>standing / walking"]
        QR["QR: camera or<br/>Zebra DataWedge"]
        PF["PositionFuser (core)<br/>kNN + trilateration + Kalman + map match"]
        UI["Compose UI<br/>Live / Set up / Test"]
        CM["CueManager<br/>banner, vibration, chime"]
        SL["SwarmLink<br/>MQTT client (Paho)"]
        WS --> PF
        MD --> PF
        QR --> PF
        PF --> UI
        PF --> SL
        SL --> CM --> UI
    end
    subgraph Server["Edge server or laptop (Docker Compose)"]
        MQ[("Mosquitto 2<br/>:1883")]
        BR["MQTT bridge"]
        CO["Coordinator<br/>live workers + ghosts"]
        EN["Engine<br/>time-window reservations"]
        API["FastAPI<br/>:8000 dashboard, sites, accuracy"]
        ST[("data/ volume<br/>site files, accuracy samples")]
        MQ <--> BR <--> CO <--> EN
        CO --> API
        API <--> ST
    end
    MAC["SwarmLocate (macOS)<br/>CoreWLAN + LocateCore"]
    SL <-- "MQTT" --> MQ
    UI -- "HTTP: site file, accuracy CSV" --> API
    MAC -- "HTTP: get site (optional)" --> API
```

| Component | Technology | Role |
| --- | --- | --- |
| Android app | Kotlin, Jetpack Compose, Paho MQTT, ZXing | Positioning, cues, setup and accuracy test on the worker's device. |
| Core library | Pure Kotlin/JVM | All positioning maths and the message format. No Android imports, fully unit-tested. |
| Mac app | Swift, SwiftUI, CoreWLAN | Locates a MacBook from the phone's recordings. Own Swift port of the matcher. |
| Broker | Eclipse Mosquitto 2 | Low-latency publish/subscribe between devices and server. |
| Swarm server | Python 3.12, FastAPI, paho-mqtt | 1 Hz engine loop, ghost workers, site and accuracy storage, dashboard. |
| Dashboard | Static HTML + JS served by FastAPI | Live map of workers and cues, ghost count control. |

## Why MQTT (and not DDS)

The blueprint compared MQTT and DDS. MQTT was chosen because:

- A broker on one laptop is enough for 5 to 30 devices. Message rate is small (2 poses per
  second per device).
- MQTT works through normal Wi-Fi networks and NAT; DDS multicast discovery often does not
  work on enterprise Wi-Fi.
- MQTT has **Last Will** messages, **retained** messages and **QoS 1**, which map directly to
  the needs: online/offline status, latest heartbeat for new clients, and cues that must arrive.

## Data flow and timing

```mermaid
sequenceDiagram
    participant P as Phone
    participant M as Mosquitto
    participant S as Swarm server
    P->>M: dev/{id}/status (retained, online=true)
    loop every 500 ms
        P->>M: dev/{id}/pose {x, y, sigma, aisle, targets}
        M->>S: pose
    end
    loop every 1 s
        S->>S: engine tick (predict 30 s, book aisles)
        S->>M: engine/heartbeat (retained)
        M->>P: heartbeat
        opt cue changed (or every 4 s while active)
            S->>M: dev/{id}/directive (QoS 1)
            M->>P: directive {state, holdS, expiresMs}
        end
    end
    Note over P: no heartbeat for 5 s → "No traffic info"
    P--xM: connection lost
    M->>S: Last Will: status online=false
```

| Rate | Message |
| --- | --- |
| 10 Hz | Phone filter clock (Kalman predict) |
| Every 5 s (setting) | Wi-Fi scan. Needs scan throttling off, or Android allows only 4 scans per 2 minutes. |
| Every 500 ms | RTT ranging, when the phone supports it |
| 2 Hz | Pose to server |
| 1 Hz | Engine tick and heartbeat |
| Every 4 s | Re-send of an active cue, which refreshes its expiry |

## Failure handling

The system is designed so that every failure ends in a **safe, quiet state**.

| Failure | What happens |
| --- | --- |
| Phone loses Wi-Fi or the broker | Paho reconnects automatically. The broker publishes the phone's Last Will and the server removes the worker from planning (also after 10 s without a pose). The phone shows grey "No traffic info" after 5 s without heartbeat. |
| Server stops | No heartbeat: every phone falls back to "No traffic info". Cues already on screen expire after `expiresMs`. |
| Position is poor | The Kalman filter rejects outliers. The phone reports `sigma` (uncertainty) so the server can see it. A QR scan resets the position. |
| A worker ignores a cue | After 5 s the engine plans *around* that worker (gives them top priority) instead of insisting. |
| Old map on a phone | The heartbeat carries `siteVersion`; the phone can see its map is old. |

## Deployment options

| Option | When |
| --- | --- |
| **Laptop** on the same Wi-Fi | Demos and first floor tests. `docker compose up`. |
| **Small edge PC** (Intel NUC class, 4 cores, 8 GB) | Longer pilots. Same Compose file, `restart: unless-stopped`. |
| **On-prem server / VM** | Production-like tests. Put Mosquitto behind authentication and TLS first (the demo broker allows anonymous access). |
