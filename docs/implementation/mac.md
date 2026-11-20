# Mac app (SwarmLocate)

SwarmLocate shows where a **MacBook** is on the floor plan, using the Wi-Fi recordings made
with the phone app. It is useful for hot-desk offices ("which desk group is this laptop
at?") and to check fingerprint quality from a second device. It does not need the server.

| | |
| --- | --- |
| Platform | macOS 13 (Ventura) or newer, Apple silicon and Intel |
| Language | Swift 5.9, SwiftUI, Swift Package Manager |
| Wi-Fi | CoreWLAN (`CWWiFiClient.scanForNetworks`) |
| Permissions | Location (macOS only reveals BSSIDs to apps with Location permission) |

## Structure

```text
mac/
├── Package.swift
├── Sources/
│   ├── LocateCore/          Foundation only, unit-tested
│   │   ├── Site.swift       Site file model (same JSON as the phone) + AP filter
│   │   └── Matcher.swift    Fingerprint matching, offsets, tracker, accuracy stats,
│   │                        Compare (scan vs recording, per AP, for the details view)
│   └── SwarmLocate/         The app
│       ├── SwarmLocateApp.swift
│       ├── AppModel.swift   State: site, scans, mode, accuracy samples, settings
│       ├── WifiScanner.swift  CoreWLAN scans + Location permission gate
│       ├── ContentView.swift  Sidebar, position card, settings
│       ├── FingerprintViews.swift  Signal strips + popovers: spot recording only; live scan, match and best matches
│       └── MapView.swift    Maps-style pan/zoom map, floor plan picture, scale bar
├── Tests/LocateCoreTests/
└── build-app.sh             Builds dist/SwarmLocate-mac-<version>.zip
```

## Matching on a laptop

The matcher uses the same dB distance, neighbour weighting and "unsure" test as
`RssiPositioning.kt` on the phone. One problem is new: **a MacBook antenna hears Wi-Fi 5 to
10 dB louder or quieter than the phone that made the recordings.** Three modes handle this:

| Mode | How it works | When |
| --- | --- | --- |
| **Automatic correction** | For each recording, the dB gap is the **median** of the per-AP differences; the scan is shifted by it before comparing. | Default. No setup. |
| **Fixed correction** | One offset, measured once by standing on a known spot and pressing *Calibrate*. | Best after calibration. |
| **Order only** | Compares the **rank order** of APs (strongest first), ignoring dB values. | When the other two keep picking the wrong spot. |

A laptop usually sits still, so the app scans every 4 seconds and **averages the last 4
scans** before matching. **I moved** clears the average; a large change in the scan also
clears it automatically.

## Accuracy check

Like the phone: stand on a recorded spot, choose it, wait for 3 scans, press **Test: I am at
this spot**. The app shows median error, 90th percentile error and correct-area rate, and
saves the same CSV columns as the phone, so `tools/accuracy_report.py` works on it.

## Build

```bash
cd mac
swift test              # LocateCore tests
swift run SwarmLocate   # quick run (Location permission works best from the .app)
./build-app.sh          # dist/SwarmLocate-mac-<version>.zip
```

The **Mac** GitHub Actions workflow builds it on every change under `mac/`. Running it by
hand with a tag such as `mac-v0.2.1` publishes a release. The app is ad-hoc signed (no paid
Apple Developer ID), so macOS asks once before opening it; the Homebrew cask removes the
quarantine flag for you. See [Install](../guide/install.md#mac-app-swarmlocate).
