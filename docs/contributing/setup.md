# Development setup

## Clone

```bash
git clone https://github.com/rmaheedharan/swarm-amr.git
cd swarm-amr
```

## Tools

| Part | You need |
| --- | --- |
| `core/` | JDK 17 (Temurin recommended). Gradle comes with the wrapper (`./gradlew`). |
| `app/` | JDK 17 + Android SDK (platform 35, build-tools 35.0.0). Android Studio Ladybug or newer is easiest. |
| `backend/` | Python 3.12, and Docker for the full stack. |
| `mac/` | macOS 13+, Xcode 15+ (Swift 5.9). |
| `docs/` | Python 3 and `pip install mkdocs-material`. |

=== "Android Studio"

    1. **File > Open** and choose the repository folder.
    2. Let Gradle sync. Android Studio installs the SDK parts it needs.
    3. Pick the `app` run configuration and a device with USB debugging on.

=== "Command line"

    ```bash
    # Android SDK without Android Studio (Linux); writes local.properties
    tools/install-android-sdk.sh

    ./gradlew :core:test
    ./gradlew :app:assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    ```

## Backend

```bash
cd backend
python3.12 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
python -m pytest -q

# run the server without Docker (needs a broker on localhost:1883, or MQTT_ENABLED=0)
MQTT_ENABLED=0 python -m swarm_server.main

# or the full stack
docker compose up --build
```

## Mac app

```bash
cd mac
swift test
swift run SwarmLocate
./build-app.sh            # dist/SwarmLocate-mac-<version>.zip
```

Open `mac/Package.swift` in Xcode to work in the IDE.

## Docs site

```bash
pip install mkdocs-material
mkdocs serve              # http://127.0.0.1:8000, reloads on save
mkdocs build --strict     # what CI runs
```

The site is in `docs/`, the menu is in `mkdocs.yml`.

## Testing on a device

- Turn on **USB debugging** (Developer options).
- Turn off **Wi-Fi scan throttling** on test phones.
- For RTT, use a phone and access points that support 802.11mc (check **Live > Traffic > Details**).
- `adb logcat -s SwarmLink` shows the MQTT link log.
