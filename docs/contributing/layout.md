# Project layout

```text
swarm-amr/
├── app/                    Android app (Kotlin, Compose). Depends on :core
│   ├── src/main/kotlin/com/swarmamr/app/
│   ├── src/test/           JVM unit tests
│   └── demo-signing.jks    Public demo signing key (not for production)
├── core/                   Pure Kotlin/JVM: site model, positioning maths, protocol
│   ├── src/main/kotlin/com/swarmamr/core/
│   └── src/test/
├── backend/                Python swarm server + Mosquitto + Docker Compose
│   ├── swarm_server/
│   ├── tests/
│   ├── mosquitto/mosquitto.conf
│   ├── Dockerfile
│   └── docker-compose.yml
├── mac/                    SwarmLocate (Swift Package)
│   ├── Sources/LocateCore/ Matching + site model, Foundation only
│   ├── Sources/SwarmLocate/ SwiftUI app
│   └── Tests/
├── tools/
│   ├── accuracy_report.py  Accuracy CSV report
│   └── install-android-sdk.sh
├── docs/                   This documentation site (MkDocs Material)
├── mkdocs.yml
└── .github/workflows/      android.yml, backend.yml, mac.yml, docs.yml
```

## Where to make a change

| I want to change... | Edit |
| --- | --- |
| How position is computed | `core/.../PositionFuser.kt`, `RssiPositioning.kt`, `Trilateration.kt`, `Kalman2D.kt` + tests in `core/src/test` |
| A screen in the Android app | `app/.../ui/` |
| Wi-Fi scanning or RTT on Android | `app/.../sensing/WifiSensors.kt` |
| How cues feel (vibration, sound, text) | `app/.../swarm/CueManager.kt` |
| When cues are sent | `backend/swarm_server/engine.py` (+ simulation evidence) |
| A message field | `core/.../Protocol.kt` **and** `backend/swarm_server/protocol.py` |
| A site file field | `core/.../Site.kt`, `backend/swarm_server/site.py`, `mac/Sources/LocateCore/Site.swift`, and `docs/site-file.md` |
| Mac matching | `mac/Sources/LocateCore/Matcher.swift` (keep in step with `RssiPositioning.kt`) |
| The dashboard | `backend/swarm_server/static/index.html` |
