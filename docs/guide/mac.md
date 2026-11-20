# Mac app guide

SwarmLocate shows where your MacBook is on the floor plan, using the Wi-Fi recordings you
made with the phone app. It does not need the server.

First [install it](install.md#mac-app-swarmlocate) and allow Location.

## 1. Load your place

1. On the phone: **Set up > ⋮ > Share place file**. It sends the `.json` place file, and the
   floor plan picture if the place has one. AirDrop them to the Mac (or use mail, Google
   Drive, a USB cable) and keep them in the **same folder**.
2. In SwarmLocate, press **Open place file…** and choose the `.json` file. The picture loads
   with the same scale as on the phone. Or get the place from the swarm server.

The map shows recorded spots (grey dots), markers (rings) and you (blue dot). The big text
says **Near D12**, or **Near D12 or D20** when two far spots look the same to Wi-Fi (the
orange ring shows the other one).

### See the Wi-Fi fingerprint

The **My position** card and the card of a chosen spot each have a small **signal strip** (a
thin bar per access point). Click it to open every detail beside the sidebar. The cards stay
small.

**Chosen spot** shows only what the phone recorded there (from the place file), nothing from
the Mac:

- Each access point's address, its recorded level (teal) and how much it moved while
  recording (grey band, "±"). Steady signals count more when matching.
- Tags: **random** (a private address, often a phone hotspot) and **ignored** (dropped by the
  access point filter).
- Summary: number of access points, scans, position, strongest level, average spread.

**My position** shows what the Mac hears now and how it picked the spot:

- **Heard now**: every network the Mac hears, with name, address, band and channel, the last
  scan (blue) and the average of recent scans (dark mark). Tags say if it is **in
  recordings** (used), **not recorded** (not used) or **ignored**.
- **Match**: the Mac (after the Mac-to-phone correction) next to the best matching recording,
  with the gap per access point (green small, orange medium, red big).
- **Best matches**: the recorded spots with the lowest scores, best first. Click one to show
  it on the map.

**Copy CSV** in each view puts the table on the clipboard for a spreadsheet.

## 2. Correct for the Mac's antenna

A MacBook hears Wi-Fi a bit louder or quieter than the phone, often 5 to 10 dB. Choose how the
app corrects this in **Mac vs phone signal**:

| Choice | When to use |
| --- | --- |
| **Automatic correction** (start here) | No setup. Works out the gap for every recorded spot. |
| **Fixed correction** | Best once calibrated. Put the laptop on a recorded spot, choose it, wait for 3 scans, press **Calibrate: I am at this spot**. |
| **Order only** | Only looks at which access points are louder than others. Try it if the other two keep showing the wrong spot. |

The Mac scans every 4 seconds and averages the last 4 scans, so it suits a laptop on a desk
better than someone walking. After you carry it to a new desk, press **I moved**.

## 3. Ignore phone hotspots

By default the app ignores access points with a random address, which most hotspots use. To
use only your office's own access points, open **Which access points** and type the start of
their address (for example `7c:b6:8d`) or the office Wi-Fi name. The place file from the phone
can carry the same setting (`apFilter`); the Mac uses it unless you change it.

## 4. Check accuracy

Put the laptop on a recorded spot, choose it, wait for 3 scans, and press **Test: I am at this
spot**. Repeat at several spots. The app shows the typical error, the 9-in-10 error and how
often it picked the right area. **Save CSV…** writes the same columns as the phone.

Expect "near this desk group" accuracy, best when the laptop sits still.

The full step-by-step guide with screenshots-level detail is in
[`mac/README.md`](https://github.com/rmaheedharan/swarm-amr/blob/main/mac/README.md).
