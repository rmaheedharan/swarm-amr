# Install

## Android app

**Needs:** Android 9 or newer. Zebra TC5x (TC52, TC57, TC58) or any modern phone.

1. On the phone, open the
   [Releases page](https://github.com/rmaheedharan/swarm-amr/releases) and download the newest
   `swarm-amr-x.y.z.apk` (tags start with `v`, for example `v0.7.1`).
2. Open the downloaded file. Android asks to allow installs from this source. Allow it, then
   tap **Install**.
3. Open **Swarm AMR**. The welcome screens ask for **Location** (Android needs it to read Wi-Fi;
   GPS is not used) and check **Wi-Fi scan throttling**.
4. **Turn off Wi-Fi scan throttling** (important, otherwise Android allows only 4 scans every 2
   minutes):
    1. **Settings > About phone** (Samsung: **> Software information**). Tap **Build number**
       7 times to turn on Developer options.
    2. **Settings > System > Developer options** (Samsung: **Settings > Developer options**).
       Turn **Wi-Fi scan throttling** off.
5. Keep Location turned on in quick settings.

!!! tip "Updates"
    New versions install over the old one and keep your data. The APK is signed with the
    same key every time.

??? info "Install with adb (developers, many devices)"
    ```bash
    adb install -r swarm-amr-0.7.1.apk
    ```
    For a fleet of Zebra devices, push the APK with your MDM (for example Zebra StageNow or
    SOTI MobiControl).

## Mac app (SwarmLocate)

**Needs:** macOS 13 (Ventura) or newer, Apple silicon or Intel.

=== "Homebrew (recommended)"

    Open **Terminal** and paste:

    ```bash
    brew tap rmaheedharan/swarm-amr https://github.com/rmaheedharan/swarm-amr
    brew install --cask swarmlocate
    ```

    This installs SwarmLocate in **Applications** and clears the download flag, so macOS
    opens it without the "Open Anyway" step.

    - Update: `brew upgrade --cask swarmlocate`
    - Remove: `brew uninstall --cask swarmlocate`
    - No Homebrew yet? See [brew.sh](https://brew.sh).

=== "Manual download"

    1. From the [Releases page](https://github.com/rmaheedharan/swarm-amr/releases), download
       `SwarmLocate-mac-x.y.z.zip` (tags start with `mac-v`).
    2. Double-click the zip and drag **SwarmLocate** into **Applications**.
    3. Open it the first time. The app has no paid Apple signature, so macOS asks once:
        - **macOS 15 or newer:** double-click, press **Done**, then **System Settings >
          Privacy & Security > Open Anyway**, enter your password, press **Open**.
        - **macOS 13 or 14:** right-click the app, choose **Open**, press **Open**.
        - If neither works:
          ```bash
          xattr -dr com.apple.quarantine /Applications/SwarmLocate.app
          ```

When the app starts, press **Allow** for Location. macOS shows Wi-Fi access point addresses
only to apps with Location permission. Your GPS position is not used.

Next: [Mac app guide](mac.md).

## Server (backend)

Only needed for the **traffic demo**, for sharing places through a server, and for collecting
accuracy results centrally. Positioning and the accuracy test work without it.

**Needs:** a laptop or small PC on the **same Wi-Fi network** as the phones, with
[Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows, Mac) or Docker
Engine (Linux).

```bash
git clone https://github.com/rmaheedharan/swarm-amr.git
cd swarm-amr/backend
docker compose up --build
```

(No git? Use the green **Code > Download ZIP** button on GitHub and unzip it.)

| Check | Expected |
| --- | --- |
| Open `http://localhost:8000` on the laptop | Dashboard with a few ghost workers walking |
| Open `http://<laptop IP>:8000` on the phone browser | Same dashboard (if not, see [Troubleshooting](troubleshooting.md)) |

Allow ports **1883** (MQTT) and **8000** (HTTP) through the laptop firewall. Then in the
app, **Settings > Traffic server**:

| Setting | Value |
| --- | --- |
| Server address | `http://<laptop IP>:8000` |
| Message broker (MQTT) | `tcp://<laptop IP>:1883` |
| This phone's name | short and unique, for example `tc57-ravi` |

!!! warning
    The demo broker has no password. Use it only on a private network.
