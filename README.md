# Swarm AMR for human pickers

[![Android](https://github.com/rmaheedharan/swarm-amr/actions/workflows/android.yml/badge.svg)](https://github.com/rmaheedharan/swarm-amr/actions/workflows/android.yml)
[![Backend](https://github.com/rmaheedharan/swarm-amr/actions/workflows/backend.yml/badge.svg)](https://github.com/rmaheedharan/swarm-amr/actions/workflows/backend.yml)
[![Mac](https://github.com/rmaheedharan/swarm-amr/actions/workflows/mac.yml/badge.svg)](https://github.com/rmaheedharan/swarm-amr/actions/workflows/mac.yml)
[![Docs](https://github.com/rmaheedharan/swarm-amr/actions/workflows/docs.yml/badge.svg)](https://rmaheedharan.github.io/swarm-amr/)

Robot-style traffic control for warehouse pickers. Android and Zebra devices find their
position from **real Wi-Fi** (Wi-Fi RTT where supported, otherwise signal-strength
fingerprints, plus QR checkpoints). A small server plans who uses each narrow aisle and sends
simple cues: **Slow, Wait, Go, Take another way**. The app also has an **accuracy test** that
measures position error against surveyed floor markers. A Mac app, **SwarmLocate**, finds a
MacBook on the same floor plan.

## 📖 Documentation

**https://rmaheedharan.github.io/swarm-amr/**

Concept and problems solved, architecture, positioning maths, traffic engine, Android / Mac /
backend implementation, user guide, field guide, API reference and contributor guide.

## Install

### Android (Android 9+, Zebra TC5x or any phone)

1. On the phone, open [Releases](https://github.com/rmaheedharan/swarm-amr/releases) and
   download the newest `swarm-amr-x.y.z.apk`.
2. Open it and allow installs from this source, then tap **Install**.
3. Open **Swarm AMR** and allow **Location** (needed to read Wi-Fi; GPS is not used).
4. Turn off **Wi-Fi scan throttling**: tap **Settings > About phone > Build number** 7 times,
   then **Developer options > Wi-Fi scan throttling** off.

### Mac (macOS 13+)

```bash
brew tap rmaheedharan/swarm-amr https://github.com/rmaheedharan/swarm-amr
brew install --cask swarmlocate
```

Or download `SwarmLocate-mac-x.y.z.zip` from
[Releases](https://github.com/rmaheedharan/swarm-amr/releases) and drag it to Applications
([first-open steps](https://rmaheedharan.github.io/swarm-amr/guide/install/#mac-app-swarmlocate)).
Allow **Location** when asked.

### Server (only for the traffic demo)

```bash
git clone https://github.com/rmaheedharan/swarm-amr.git
cd swarm-amr/backend
docker compose up --build
```

Dashboard: `http://<laptop IP>:8000`. MQTT for phones: `tcp://<laptop IP>:1883`.

Next: [Quick start](https://rmaheedharan.github.io/swarm-amr/guide/quickstart/) ·
[Field guide](https://rmaheedharan.github.io/swarm-amr/field-guide/)

## Repository

| Path | What |
| --- | --- |
| `app/` | Android app (Kotlin, Jetpack Compose) |
| `core/` | Positioning maths and message format, pure Kotlin, unit-tested on the JVM |
| `backend/` | Swarm server (Python, FastAPI, MQTT), Mosquitto, dashboard |
| `mac/` | SwarmLocate Mac app (Swift, SwiftUI) |
| `tools/` | Accuracy CSV report, Android SDK installer |
| `docs/` | Documentation site source (MkDocs) |

## Contributing

1. Fork and clone, then branch from `main`.
2. Make your change with tests. Keep positioning maths in `core/`.
3. Run the checks for what you touched:
   ```bash
   ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug   # Android + core
   cd backend && pip install -r requirements-dev.txt && python -m pytest -q
   cd mac && swift test
   pip install mkdocs-material && mkdocs build --strict          # docs
   ```
4. Open a pull request against `main`.

Full guide: [Contributing](https://rmaheedharan.github.io/swarm-amr/contributing/) ·
[CONTRIBUTING.md](CONTRIBUTING.md)

## Honest numbers

Simulation results are labelled as simulation. Position accuracy is only reported from the
accuracy test on a real floor.

## License

[Apache 2.0](LICENSE)
