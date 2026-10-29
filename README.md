# Swarm AMR for human pickers

A proof of concept of "swarm" traffic coordination for warehouse pickers who carry Android
or Zebra devices. Phones find their position from **real Wi-Fi** (RTT when available,
otherwise signal-strength fingerprints, plus QR checkpoints). A small server plans who uses
each narrow aisle and sends simple cues: **Slow, Wait, Go, Reroute, New order**. The app
also has an **accuracy test** that measures position error against surveyed floor markers.

**Start here:** [docs/field-guide.md](docs/field-guide.md) (set up a site, run the accuracy test and the demo).

## Parts

| Path | What |
| --- | --- |
| `app/` | Android app (Kotlin, Jetpack Compose), Android 9+. Tabs: Live, Accuracy, Survey, Settings. |
| `core/` | Positioning maths and message format, plain Kotlin, unit-tested on the JVM. |
| `backend/` | Swarm server (Python, FastAPI, MQTT), Mosquitto broker, live dashboard. `docker compose up`. |
| `tools/` | `accuracy_report.py` (CSV report), `install-android-sdk.sh`. |
| `docs/` | Field guide, site file and message format. |

## Get the app

Every push to `main` builds an APK in GitHub Actions (**Actions > Android > Artifacts**).
Every `v*` tag (for example `v0.1.0`) also publishes it on the **Releases** page.

Signing: without secrets the APK is signed with the CI debug key, which is fine for a demo.
For a stable key, add these repository secrets: `SIGNING_KEYSTORE_BASE64`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Run the server

```
cd backend
docker compose up --build
```

Dashboard: `http://<laptop IP>:8000`. MQTT for phones: `tcp://<laptop IP>:1883`.
Printable QR labels for the floor markers: `http://<laptop IP>:8000/labels/<site id>`.

## Develop

```
./gradlew :core:test                    # core tests, no Android SDK needed
./gradlew :app:assembleDebug            # needs the Android SDK (tools/install-android-sdk.sh)
cd backend && pip install -r requirements-dev.txt && python -m pytest -q
```

## Honest numbers

Simulation results (in the blueprint) are labelled as simulation. Position accuracy is only
reported from the accuracy test on a real floor.
