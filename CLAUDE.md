# Swarm AMR for human pickers: project instructions

## Goal (from the owner)

Build a ready-to-deploy Android app that demonstrates "Swarm AMR" traffic coordination for
warehouse pickers carrying Zebra devices, using **real Wi-Fi data**, and that **measures
positioning accuracy**. Work autonomously until this is done. Ask the owner only for things
that need their hands (installing on a device, walking a test, creating accounts or repos,
changing settings).

Background design: the blueprint doc "Swarm AMR for Human Operators: PoC Blueprint"
(claude.ai/artifact/PsS4szTQgSHBaVxy3bz3fT). Simulation results: `/mnt/project-files/swarm-demo/`.

## Definition of done

1. A signed, installable APK is produced by CI (GitHub Actions) on every push to `main`
   and attached to a GitHub Release on every `v*` tag.
2. The app runs on Android 9+ (API 28+), tested first on Zebra TC5x and any modern phone.
3. **Positioning from real Wi-Fi:** Wi-Fi RTT (802.11mc FTM) when APs support it, otherwise
   Wi-Fi RSSI fingerprinting, plus QR checkpoint reset (DataWedge or camera-free intent).
4. **Accuracy test mode:** walk to surveyed floor markers, tap "I am here", the app records
   estimate vs truth, shows p50 / p90 error and correct-aisle %, exports CSV.
5. **Swarm demo mode:** the app sends pose over MQTT to the backend; the backend engine
   (with optional simulated "ghost" workers) sends Normal / Warning / Hold / Go / Reroute
   cues; the app shows a banner, vibrates and plays a chime for each state.
6. Backend runs with one command: `docker compose up` (Mosquitto + swarm server + dashboard).
7. A field guide in `docs/` tells a non-developer how to set up a site and run the test.

## Layout

| Path | What |
| --- | --- |
| `core/` | Pure Kotlin/JVM library: site model, trilateration, fingerprint kNN, Kalman filter, map matching, accuracy stats, MQTT message format. No Android imports. Fully unit-tested. |
| `app/` | Android app (Kotlin, Jetpack Compose). Depends on `:core`. |
| `backend/` | Python 3.12 swarm server (FastAPI + paho-mqtt + engine), Mosquitto config, Docker Compose. |
| `tools/` | Offline analysis scripts (accuracy CSV report). |
| `docs/` | Field guide, site file format, test procedure. |
| `.github/workflows/` | CI: Android build + tests, backend tests. |

## Commands

- Core tests (no Android SDK needed): `./gradlew :core:test`
- App debug build (needs Android SDK): `./gradlew :app:assembleDebug`
- All checks: `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug`
- Backend tests: `cd backend && python -m pytest -q`
- Backend run: `cd backend && docker compose up --build`

## Conventions

- Units: metres, seconds, site frame (origin at a building corner, x along main aisles).
- Keep positioning math in `core/` so it is testable on the JVM. Android code stays thin.
- Topic root: `swarm/v1/{site}/`. JSON payloads, schema in `core/.../Protocol.kt` and
  mirrored in `backend/swarm_server/protocol.py`. Change both together.
- Never claim accuracy numbers that were not measured on a real floor. Simulation numbers
  are labelled as simulation.
- Owner reads simple English. Replies to the owner: short, plain words, no jargon without
  a one-line explanation.

## Environment notes

- The cloud container has no Android SDK by default. `dl.google.com` must be allowed in
  the environment network settings to download it (then run `tools/install-android-sdk.sh`).
  Until then, build the app only in CI.
- Docker daemon is not available in the container; test the backend with pytest directly.
