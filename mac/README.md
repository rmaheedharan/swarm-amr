# SwarmLocate for Mac

Shows where your MacBook is on the floor plan, using the Wi-Fi recordings you made with the
phone app. It does not need the server.

Needs macOS 13 (Ventura) or newer. Works on Apple silicon and Intel Macs.

## 1. Get the app

**Easiest: with Homebrew.** Open **Terminal** and paste:

```
brew tap rmaheedharan/swarm-amr https://github.com/rmaheedharan/swarm-amr && brew install --cask swarmlocate
```

This puts SwarmLocate in Applications and clears the download flag, so macOS opens it
without the "Open Anyway" step. To update later: `brew upgrade --cask swarmlocate`.
To remove: `brew uninstall --cask swarmlocate`. (No Homebrew yet? See https://brew.sh.)

**Or download it by hand.** Open the project's GitHub page, go to **Releases**, and download
`SwarmLocate-mac-x.y.z.zip`. Double-click the zip and drag **SwarmLocate** into
**Applications**. Then do step 2.

## 2. Open it the first time (only for a hand download)

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

1. On the phone: **Setup > ⋮ > Share place file**. It sends the `.json` file and the floor plan
   picture. AirDrop both to the Mac (or use mail, Google Drive, a USB cable). Keep them in the
   same folder.
2. In SwarmLocate press **Open place file…** (or the folder button at the top) and choose the
   `.json` file. The picture loads by itself, with the same scale as on the phone.
   Or get the place from the swarm server: **Settings > Place > Get from server…**.

## 5. Use the map

It works like Apple Maps:

- **Move** the map: drag it, or slide two fingers on the trackpad.
- **Zoom**: pinch on the trackpad, use the mouse wheel, double-click, or the **+ / −** buttons.
- **Blue dot** = where Wi-Fi says you are. The light blue circle shows how sure it is.
  An orange dashed ring with **?** means another far spot looks the same to Wi-Fi.
- **Arrow button** (top right) follows you again. The **corners button** shows the whole floor.
- **Sidebar** (left): search spots, see **My position** ("Near D12"), and the spots near you.
  The sidebar button at the top hides it.
- Click a spot (on the map or in the list) to see its card, with **Calibrate** and
  **Test accuracy**.
- In Dark Mode the floor plan picture is darkened too. Turn this off in **Settings > Place**.

No picture from the phone? Press the picture button at the top, choose the plan, then click both
ends of a length you know and type it in metres (same as on the phone). **Settings > Place >
Set scale…** does this again later.

## 6. Ignore phone hotspots

Phone and laptop hotspots move and switch off, so they spoil the match. By default the app
ignores access points with a random address, which most hotspots use. To use only your
office's own access points, open **Settings > Access points**, type the start of their address
or the office Wi-Fi name, for example `7c:b6:8d`, and press **Apply**. The place file from the
phone can carry the same setting (`apFilter`, see `docs/site-file.md`); the Mac uses it unless
you change it here. In the list, crossed-out access points are ignored.

## 7. Correct for the Mac's antenna

A MacBook hears Wi-Fi a bit louder or quieter than the phone, often 5 to 10 dB. Choose how the
app corrects this in **Settings > Signal**:

| Choice | When to use |
| --- | --- |
| **Automatic correction** (start here) | No setup. Works out the gap for every recorded spot. |
| **Fixed correction** | Best once calibrated. Put the laptop on a recorded spot, click that spot, wait for 3 scans, press **Calibrate** on its card. |
| **Order only** | Only looks at which access points are louder than others. Try it if the other two keep showing the wrong spot. |

The Mac scans every 4 seconds and averages the last 4 scans, so it fits a laptop on a desk
better than someone walking. After you carry it to a new desk, press **I moved** (the app also
notices big changes by itself).

## 8. Check accuracy

Put the laptop on a recorded spot, click it, wait for 3 scans, and press **Test accuracy** on its
card. Do this at several spots. **Settings > Accuracy** shows the typical error, the error 9 in
10 checks stay within, and how often it picked the right area. **Save CSV…** writes the same
columns as the phone, so `python3 tools/accuracy_report.py file.csv` works on it.

On macOS 26 and newer the panels use the Liquid Glass look; older macOS gets frosted panels.

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
