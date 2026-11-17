# SwarmLocate for Mac

Shows where your MacBook is on the floor plan, using the Wi-Fi recordings you made with the
phone app. It does not need the server.

Needs macOS 13 (Ventura) or newer. Works on Apple silicon and Intel Macs.

## 1. Get the app

1. Open the project's GitHub page, go to **Releases**, and download `SwarmLocate-mac-x.y.z.zip`.
   (Or **Actions > Mac > the newest run > Artifacts > swarmlocate-mac**, then unzip it once more.)
2. Double-click the zip. Drag **SwarmLocate** into **Applications**.

## 2. Open it the first time

The app is not from the App Store and has no paid Apple signature, so macOS asks once.

- **macOS 15 (Sequoia) or newer:** double-click SwarmLocate. macOS says it cannot check it.
  Press **Done**. Open **System Settings > Privacy & Security**, scroll down, and press
  **Open Anyway** next to SwarmLocate. Enter your password, then press **Open**.
- **macOS 13 or 14:** right-click (or Control-click) SwarmLocate, choose **Open**, then press
  **Open** again.

If neither works, open **Terminal** and run this once, then double-click the app:

```
xattr -dr com.apple.quarantine /Applications/SwarmLocate.app
```

## 3. Allow Location

macOS shows which Wi-Fi access point is which only to apps with Location permission. The app
asks when it starts: press **Allow**. It does not use your GPS position.

If you pressed "Don't Allow": **System Settings > Privacy & Security > Location Services**,
turn on Location Services and **SwarmLocate**. The app has a button that opens this page.

## 4. Load your place

1. On the phone: **Setup > ⋮ > Share place file**. Send the `.json` file to the Mac (AirDrop,
   mail, Google Drive, USB cable).
2. In SwarmLocate press **Open place file…** and choose it. Or press **From server…** if the
   phone sent the place to the swarm server.
3. If you use a floor plan picture on the phone, press **Picture…** and choose the same picture.

The map shows the recorded spots (grey dots), markers (rings) and you (blue dot). The big text
says **Near D12**, or **Near D12 or D20** when two far spots look the same to Wi-Fi (the
orange ring is the other one).

### Ignore phone hotspots

Phone and laptop hotspots move and switch off, so they spoil the match. By default the app
ignores access points with a random address, which most hotspots use. To use only your
office's own access points, open **Which access points** and type the start of their address
or the office Wi-Fi name, for example `7c:b6:8d`, then press **Apply**. The place file from the
phone can carry the same setting (`apFilter`, see `docs/site-file.md`); the Mac uses it unless
you change it here. In the Wi-Fi list, crossed-out access points are ignored.

## 5. Correct for the Mac's antenna

A MacBook hears Wi-Fi a bit louder or quieter than the phone, often 5 to 10 dB. Choose how the
app corrects this in **Mac vs phone signal**:

| Choice | When to use |
| --- | --- |
| **Automatic correction** (start here) | No setup. Works out the gap for every recorded spot. |
| **Fixed correction** | Best once calibrated. Put the laptop on a recorded spot, choose it in the list (or click it on the map), wait for 3 scans, press **Calibrate: I am at this spot**. |
| **Order only** | Only looks at which access points are louder than others. Try it if the other two keep showing the wrong spot. |

The Mac scans every 4 seconds and averages the last 4 scans, so it fits a laptop on a desk
better than someone walking. After you carry it to a new desk, press **I moved** (the app also
notices big changes by itself).

## 6. Check accuracy

Put the laptop on a recorded spot, choose it, wait for 3 scans, and press **Test: I am at this
spot**. Do this at several spots. The app shows the typical error, the error 9 in 10 checks
stay within, and how often it picked the right area. **Save CSV…** writes the same columns as
the phone, so `python3 tools/accuracy_report.py file.csv` works on it.

## Build it yourself

```
cd mac
swift test            # matching tests
swift run SwarmLocate # quick run (Location permission works best from the .app)
./build-app.sh        # dist/SwarmLocate-mac-<version>.zip
```

GitHub Actions builds it on every change under `mac/` (workflow **Mac**). To publish a release,
run the **Mac** workflow by hand and type a tag such as `mac-v0.1.0`.

The matching code is in `Sources/LocateCore/Matcher.swift`. It uses the same dB distance and
neighbour weighting as `core/.../RssiPositioning.kt` on the phone; keep them in step.
