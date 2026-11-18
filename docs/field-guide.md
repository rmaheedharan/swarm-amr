# Swarm AMR field guide

This guide is for the person who sets up a demo on a real floor or in an office. You do not
need to be a developer. The app itself also guides you: the **Set up** tab shows the next step.

| Part | What you do | Time |
| --- | --- | --- |
| 1 | Get the app on the phone | 10 min |
| 2 | Start the server on a laptop (only for the traffic demo and sharing) | 15 min |
| 3 | Set up: floor plan and markers | 20 to 60 min |
| 4 | Teach Wi-Fi (guided walk) | 20 s per marker |
| 5 | Test accuracy (guided walk) | 10 s per marker |
| 6 | Run the traffic demo | as long as you like |
| 7 | Optional: find a MacBook with the same Wi-Fi recordings | 10 min |

Words used here:

- **Aisle / walkway**: a path people walk along. In an office, each walkway between desk rows is an "aisle". They are numbered A0, A1, A2 ... from the left.
- **Narrow aisle**: only one person fits at a time. The server only plans traffic in these.
- **Marker**: a spot you can stand on, for example a desk. It can have a QR code (any text, like a desk number) or just a name. The app knows its exact position, and uses markers to learn Wi-Fi and to measure accuracy.
- **Access point (AP)**: a Wi-Fi box on the ceiling or wall.
- **RTT**: some newer APs and phones can measure the distance between them from radio travel time. This is the most exact method (often 1 to 2 m).
- **Teach Wi-Fi**: the app records "which APs I hear, and how strong" at each marker. Works on every phone.
- **Ghost**: a fake worker that only exists on the server. Ghosts make traffic so one phone can see Wait and Go alerts.

---

## Part 1. Get the app on the phone

1. On the phone, open the project's GitHub page, go to **Releases**, and download the newest
   `swarm-amr-x.y.z.apk`.
2. Open the file. Android asks to allow installs from this source. Allow it, then install.
   From version 0.2.0 on, updates install over the old app and keep your data. (Going from 0.1.x to 0.2.0, uninstall the old app first.)
3. Open **Swarm AMR**. The welcome screens ask for **Location** (Android needs it to read Wi-Fi; the app does not use GPS) and check **Wi-Fi scan throttling**.
4. Keep Location turned on in the phone's quick settings.

### Turn off Wi-Fi scan throttling (important)

Android allows only 4 Wi-Fi scans every 2 minutes. That is too slow.

1. Open phone **Settings > About phone** (on Samsung: **> Software information**). Tap **Build number** 7 times. This turns on Developer options.
2. Go to **Settings > System > Developer options** (on Samsung: **Settings > Developer options**). Turn **Wi-Fi scan throttling** off.

The app shows a yellow warning while throttling is on, with a button that opens Developer options. **Settings > Wi-Fi scan throttling** shows the state.

### Does my phone have RTT?

**Live > Traffic > Details > Wi-Fi RTT on this phone**. If it says **no**, the app uses the Wi-Fi recordings from Part 4. Many Samsung Galaxy A phones have no RTT. Google Pixel phones and newer Zebra devices often do.

---

## Part 2. Start the server on a laptop

You need a laptop on the **same Wi-Fi network** as the phones.

1. Install **Docker Desktop** (Windows or Mac) or Docker Engine (Linux).
2. Download this project from GitHub (green **Code** button > **Download ZIP**) and unzip it.
3. Open a terminal in the `backend` folder and run:

   ```
   docker compose up --build
   ```

   The first time takes a few minutes. Leave the window open.

4. Find the laptop's IP address on the Wi-Fi:
   - Windows: run `ipconfig`, look for "IPv4 Address" under Wi-Fi (for example `192.168.1.10`).
   - Mac: **System Settings > Wi-Fi > Details > IP address**.
5. On the laptop, open `http://localhost:8000`. You should see the dashboard with a few ghosts walking.
6. Allow ports **1883** and **8000** through the laptop's firewall (Windows asks the first time; click **Allow** for private networks).
7. In the app, tap the gear (top right) to open **Settings**. Under **Traffic server**, tap each row and fill in:
   - **Server address**: `http://<laptop IP>:8000`
   - **Message broker (MQTT)**: `tcp://<laptop IP>:1883`
   - **This phone's name**: a short name, for example `a52s-ravi`

To check: on the phone's browser, open `http://<laptop IP>:8000`. If the dashboard opens, the network is fine.

Notes:
- Many office and guest Wi-Fi networks block phone-to-laptop traffic ("client isolation"). If the phone cannot open the dashboard, use a phone hotspot or a small travel router for the demo.
- The demo broker has no password. Use it only on a private network.
- Change the number of ghosts on the dashboard (top right), or start with `GHOSTS=5 docker compose up`.

---

## Part 3. Set up: floor plan and markers

Open the **Set up** tab. It is a checklist; the next step is highlighted.

### 3.1 Floor plan

Tap **Floor plan**.

1. Give the place a name.
2. Choose **Warehouse**, **Walkways** (an office or home with clear walkways between desk rows) or **Open floor** (no fixed paths).
3. For **Open floor**, only set the **Width** and **Depth** of the rectangle that holds the floor, then go to Part 4A. Otherwise use the **−** and **+** buttons to set:
   - **Number of aisles** (or walkways).
   - **Length of each aisle**, in metres.
   - **Space between aisles**: centre of one aisle to the centre of the next.
4. Tap the **narrow** aisles (one person at a time). In an office you can leave them all off.
5. Check the **Preview**, then tap **Save**.

#### Use a picture of your floor plan (best for offices)

If you have a drawing of the floor (a PNG or JPG, or a screenshot of a PDF), use it as the map:

1. On **Floor plan**, under **Floor plan picture**, tap **Choose picture** and pick the file.
2. **Set the scale.** Zoom in and tap one end of something you know the length of, then the other end. A long thing is best, for example a conference table marked 1071 cm, or a wall with its size written on the plan.
3. Type the real length. Choose **cm** or **m**. The app shows how big the whole floor is. If that looks wrong, tap **Start again**.
4. Tap **Save**. The app opens **Record Wi-Fi** and you can start at once.

With a picture you do not choose Warehouse or Walkways: the floor is open and the picture sets its size. To fix the scale later, open **Floor plan > Set scale**. The picture stays on this phone only. On another phone, choose the same picture and set the scale again.

If there are no markers yet, the app asks **Make markers for you?** Choose **Choose names** to put a marker every few metres on every aisle, named your way (see below). Choose **Not now** if you will scan your own desk QR codes.

### Your own marker codes (like L0432)

**Markers > ⋮ > Make markers on every aisle** opens **Name markers**:

- Pick an example (`L0432`, `A3-07` or `0001`) or type a **Pattern**. `{AA}` is the aisle number, `{SS}` the spot number in that aisle, `{NNNN}` a running number. More letters give more leading zeros. Other text stays as it is. So `L{AA}{SS}` gives `L0432` for aisle 4, spot 32.
- Set the **first aisle number**, the **first spot number** and **one marker every** (distance).
- The preview shows the first and last names. Tap **Make**.

Changing only the names later keeps the Wi-Fi recordings at the same spots.

All positions are in metres from one corner (x across the aisles, y along them). You only need **More settings** if your access points or markers already use other numbers.

### 3.2 Markers

Tap **Markers**, then **Add marker**:

- **Scan its QR code**: the code's text (for example `DESK-12`) becomes the marker's code. On a Zebra device you can also press the scan button.
- **No QR code? Type a name**: for example `Desk 12`. You can add the code later.

Then:

1. Optional: type a **Name to show**, for example "Desk 12, window side".
2. **Tap the map** where the marker is. Keep **Put it on the nearest walkway** on: the app always shows people on a walkway.
   If you are standing there and positioning works, **Use my current position** also works.
3. Tap **Save**.

Tap any marker on the map or in the list to change or delete it. Scanning a code on this screen opens that marker, or starts a new one.

For a first test, 10 to 20 markers spread over 3 or 4 aisles is enough.

Printed QR labels for automatic markers: send the place to the server (**Set up > ⋮ > Send place to server**), then open `http://<laptop IP>:8000/labels/demo` and print.

### 3.3 Access points (only for RTT)

Shown only when the phone supports RTT. Tap **Access points**.

1. Under **Seen now**, tap an access point ("supports RTT" is best), then tap the map where it is.
2. For RTT you need at least 3 RTT access points in range everywhere.
3. Stand on a marker and tap **Calibrate distances at a marker**. Choose the marker. Do this at 2 or 3 markers.

### Sharing a place with other phones

**Set up > ⋮ (top right)**:
- **Share place file / Open place file**: send it by WhatsApp, email or Drive. No server needed. If the place has a floor plan picture, it is sent too, with the file name the place file expects (the Mac app finds it next to the file).
- **Send place to server / Get place from server**: with the server from Part 2.

---

## Part 4A. Record Wi-Fi anywhere (fastest, any floor)

You do not need markers first. **Set up > Record Wi-Fi anywhere** (on an open floor, **Teach Wi-Fi** opens it too).

1. Stand somewhere. **Tap the map** where you stand. A pin drops. (Or tap **Type position** to enter x and y.)
2. The spot gets a name like `P001`. To use your own code, type it, or scan the QR code at the spot.
3. Tap **Record** and stand still 20 seconds. The spot is saved and turns green.
4. Walk 2 to 3 metres and repeat. Cover the whole floor.

### Walk and record (much faster)

Instead of standing at many spots, record while you walk:

1. Tap the map where you stand. A pin drops.
2. Tap **Walk**. Walk **slowly**, at an even pace, in a **straight line**, with the phone in front of you.
3. When you stop, **tap the map where you are**.

The app spreads the Wi-Fi readings along the line, one recording about every 1.5 m. Walks are named `W1`, `W2` and so on (pieces are `W1.1`, `W1.2`, ...). If you tapped the wrong end point, tap **Undo** on the message at the bottom, or **⋮ > Remove walk** later.

Tips: keep each walk short (5 to 15 m, along a row of desks or a corridor). Walk both ways along a corridor. Turn off Wi-Fi scan throttling first (Part 1), or a walk gets very few readings. Standing recordings are still best at important spots, like desks you want to tell apart.

### Ignore phone hotspots

Phones and laptops can make their own Wi-Fi (a hotspot). If one is on while you record, the app learns it, and later it is gone or somewhere else, so positions go wrong. In **Settings > Access points to use**:

- **Ignore phone hotspots** (on by default) skips the made-up addresses most hotspots use.
- **Office access points only**: type the first part of your office access point addresses, for example `7c:b6:8d`. The app suggests the one most of your recordings share. You can also give your **Office Wi-Fi names**.
- **Clean recordings** removes ignored access points from what you already recorded.

### Coverage colours

The **layers** button at the top shows how well the floor is covered:

- **Green**: there is a recording within about 2 m.
- **Grey**: nothing recorded near here yet. Walk or record here.
- **Red**: the recording here looks like another one far away, so the app may mix them up. Record here again while turning around, and add recordings between.

The card at the top shows how much of the floor is covered. Aim for no grey in the places people work, and no red.

If a spot already has a code on the map, just scan it: recording starts at once. To record a spot again, tap its dot.

While it records, turn around slowly, once. Your body blocks Wi-Fi, so this teaches the app what the spot looks like from every side, and how steady each signal is.

If the screen says **spots look alike**, those two places have almost the same Wi-Fi and the app may mix them up. Record them again, and add a spot between them. On the Live tab, the app then says **Near A or B** when it cannot tell; stand still a few seconds (it averages the scans) or scan a code.

The spots you record become markers, so you can use them later for the accuracy test.

## Part 4. Teach Wi-Fi (guided walk)

Do this with the same phone model you will test with. QR codes are not needed.

1. **Set up > Teach Wi-Fi**.
2. The card says **Go to: <marker>**, and the marker is pink on the map. Walk there.
3. Hold the phone at chest height. Tap **I am here, record** and stand still for 20 seconds. The ring counts down; the phone buzzes when done.
4. The next marker pops up (the nearest one not done yet). Repeat.
   - **Skip this spot** if you cannot reach it now.
   - Tap any marker on the map to go there instead.
   - If the marker has a QR code, just scan it: recording starts at once.
5. Green markers are done. Recording a marker again replaces the old recording.

Tips:
- Do it when the place looks like it does during work (doors, pallets, people).
- If an access point is moved or replaced, record the markers near it again.

---

## Part 5. Test accuracy (guided walk)

This is the main test: how close is the app's position to the truth?

1. Walk around for a minute so the app settles.
2. **Test > Start test**.
3. Walk to the marker on the card, stand on it, tap **I am here, record**, and stand still 10 seconds.
   After each marker, a message says how far off the app was, and if it named the right aisle.
4. Cover at least **20 markers**, in at least 3 aisles. Then do a second round in a different order.

Scanning a marker's QR code during the test only tells the app which marker you are at. It does not reset the position, so the result stays honest. (On the **Live** tab, a scan does reset the position.)

The **Test** tab shows:
- **Half of readings within**: the median error.
- **9 of 10 readings within**: the 90th percentile error.
- **Right aisle**: how often the app named the right aisle. This matters most for traffic alerts.
- **Good enough for traffic cues** when right aisle is 95% or more **and** 9 of 10 readings are within 2 m.
- **By marker, worst first**: where to record Wi-Fi again or add markers.

Save the results: the **share** button (top right) sends a CSV file. **⋮ > Send results to server** uploads them. On a laptop, `python3 tools/accuracy_report.py accuracy.csv` prints more tables.

Only report numbers measured this way. Simulation numbers are not floor numbers.

---

## Part 6. Traffic demo

1. Server running (Part 2) and server address filled in.
2. **Live > Traffic**: turn on **Traffic demo**. It should say "Connected to the server". The phone appears on the dashboard.
3. Walk to the pink circle on the map. Stand there 6 s to finish the pick, or tap **Pick done, next**.
4. Set 4 to 8 ghosts on the dashboard so there is traffic in the narrow aisles.
5. Follow the coloured banner at the top of the Live tab:

| Banner | Feel | What to do |
| --- | --- | --- |
| amber, "Slow down" | 2 short buzzes | Walk slower; someone will use that aisle soon. |
| blue, "Wait N s" | 1 long buzz | Stop at the aisle entrance. Do not enter. |
| green, "Go" | 1 short buzz | The aisle is free. Walk. |
| purple, "Take another way" | 3 short buzzes | Use another aisle to reach your pick. |
| purple, "Next pick changed" | 3 short buzzes | Follow the new pink circle. |
| grey, "No traffic info" | 1 tiny buzz | No server for 5 s. Use your own eyes. |

Practise the buzz patterns first: **Settings > Practise the alerts**.

The Live map fills the screen. Pinch to zoom, drag to move. The round button on the right keeps your dot in the middle (follow me). If the position is wrong, tap **Scan** and scan the code where you stand. The traffic demo switch and the details are under **Traffic**.

Safety: alerts are a help, not a safety system. People must still look before they enter an aisle.

---

## Part 7. Find a MacBook (optional)

The **SwarmLocate** Mac app uses the phone's Wi-Fi recordings to show where a MacBook is.
Share the place file from the phone (**Setup > ⋮ > Share place file**), open it in the Mac app,
and allow Location when asked. Expect "near this desk group" accuracy, best when the laptop
sits still. Steps, including how to open an app from outside the App Store:
[Mac app guide](guide/mac.md) (full details in `mac/README.md`).

## Problems and fixes

| Problem | Fix |
| --- | --- |
| "Finding you…" for a long time | Location must be on. Turn off scan throttling. Do Teach Wi-Fi. Scan a code to start at a known spot. |
| Position jumps between aisles | Record Wi-Fi at more markers, especially at aisle ends. Check access point positions. |
| Yellow "Android is slowing Wi-Fi scans" warning | Turn off scan throttling (Part 1). |
| "Cannot reach the server" | Same Wi-Fi? Firewall allows 1883 and 8000? Broker address starts with `tcp://`? Try a phone hotspot. |
| "Code is not on the map" when scanning | Tap **Add it** to make a marker, or get the newest place file. |
| The app stops in the background | Phone **Settings > Apps > Swarm AMR > Battery > Unrestricted**. |
| I want to see the welcome screens again | **Settings > Show the welcome screens again**. |
