# Swarm AMR field guide

This guide is for the person who sets up a demo on a real floor. You do not need to be a
developer. Follow the parts in order.

| Part | What you do | Time |
| --- | --- | --- |
| 1 | Get the app on the phone | 10 min |
| 2 | Start the server on a laptop | 15 min |
| 3 | Tell the app about the floor (layout, markers, access points) | 30 to 60 min |
| 4 | Teach the app the Wi-Fi (survey walk) | 2 min per marker |
| 5 | Measure accuracy | 30 min |
| 6 | Run the swarm demo | as long as you like |

Words used here:

- **Aisle**: a walkway between two racks. Aisles are numbered A0, A1, A2 ... from the origin corner.
- **Narrow aisle**: an aisle where two people cannot pass each other. The server only plans traffic in these.
- **Marker**: a surveyed point on the floor with a QR label. The app knows its exact position. We use markers to measure accuracy.
- **Access point (AP)**: a Wi-Fi box on the ceiling or wall.
- **RTT**: "round trip time". Some newer APs and phones can measure the distance between them from radio travel time. This is the most exact method (often 1 to 2 m).
- **Fingerprint**: a record of "which APs I hear, and how strong" at a marker. Works on every phone, but needs a survey walk.
- **Ghost**: a fake worker that only exists on the server. Ghosts make traffic so one phone can see Hold and Go cues.

---

## Part 1. Get the app on the phone

1. On the phone, open the project's GitHub page, go to **Releases**, and download the newest
   `swarm-amr-x.y.z.apk`. (Builds that are not released yet are under **Actions > Android > the newest run > Artifacts**.)
2. Open the file. Android asks to allow installs from this source. Allow it, then install.
3. Open **Swarm AMR**. Go to **Live** and press **Start**. Allow **Location**, **Nearby devices** and **Notifications**.
4. Turn on Location (GPS) in the phone's quick settings. Android needs it to read Wi-Fi, even indoors.

### Turn off Wi-Fi scan throttling (important)

Android allows only 4 Wi-Fi scans every 2 minutes. That is too slow for positioning by signal strength.

1. Open phone **Settings > About phone > Software information**. Tap **Build number** 7 times. This turns on Developer options.
2. Go back to **Settings > Developer options**. Find **Wi-Fi scan throttling** and turn it **off**.

In the app, **Settings > Open Developer options** takes you there. The **Settings** tab also shows "Wi-Fi scans asked / refused". If "refused" keeps going up, throttling is still on.

### Does my phone have RTT?

Look at **Live > Wi-Fi RTT on this phone**.

- **yes**: good. RTT works if your access points also support it (802.11mc / "FTM").
- **no (signal strength only)**: the app uses fingerprints. You must do Part 4.

Many phones (for example many Samsung Galaxy A models) do not support RTT. Google Pixel phones do. Zebra TC52/TC57 and newer often do.

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
7. In the app, open **Settings** and fill in:
   - **MQTT broker**: `tcp://<laptop IP>:1883`
   - **Backend**: `http://<laptop IP>:8000`
   - **This device name**: a short name, for example `a52s-ravi`
   - Press **Save**, then Stop and Start positioning on the Live tab.

To check: on the phone's browser, open `http://<laptop IP>:8000`. If the dashboard opens, the network is fine.

Notes:
- Many office and guest Wi-Fi networks block phone-to-laptop traffic ("client isolation"). If the phone cannot open the dashboard, use a phone hotspot or a small travel router for the demo.
- The demo broker has no password. Use it only on a private network.
- Change the number of ghosts on the dashboard (top right), or start with `GHOSTS=5 docker compose up`.

---

## Part 3. Tell the app about the floor

All positions are in **metres** from one corner of the building (the **origin**). x runs across the aisles, y runs along the aisles.

```
 y ↑   top cross-aisle
   |  ┌──┐  ┌──┐  ┌──┐
   |  │R │A0│R │A1│R │ ...      R = rack, A = aisle
   |  └──┘  └──┘  └──┘
   +──bottom cross-aisle──────→ x
 origin (0, 0)
```

### 3.1 Layout

In the app: **Survey > Edit layout**. Measure with a tape or laser meter:

| Field | How to measure |
| --- | --- |
| Origin x, y | Where the grid starts. Usually 0, 0 at the corner you chose. |
| Aisle to aisle distance | Centre of one aisle to the centre of the next, in metres (often 2.5 to 3.5 m). |
| Number of aisles | Count them. |
| Cell length along aisle | About 1.2 m works well. The aisle length is this times the number of cells. |
| Aisle length (cells) | Aisle length in metres ÷ cell length. Round to a whole number. |
| Narrow aisles | Aisles where two people with carts cannot pass, for example `1,2,5`. |

Press **Save**. The app makes markers every 2 cells in every aisle, named like `A3-07` (aisle 3, cell 7). Use **Re-make floor markers** after you change the layout.

### 3.2 Floor markers and QR labels

1. Press **Send to server** on the Survey tab.
2. On the laptop, open `http://localhost:8000/labels/demo` and print the labels (A4).
3. For each label, find its point using the x and y printed under the code, and stick the label on the floor there. Accuracy can be no better than how well you place these, so measure carefully (10 cm is fine).

You do not need all markers. For a first test, 10 to 20 markers spread over 3 or 4 aisles is enough. Include narrow aisles.

### 3.3 Access points

Only needed for RTT, or for the fallback "centroid" method. Skip if you will only use fingerprints.

1. On the **Survey** tab, the list "Access points seen now" shows every AP the phone hears. "· RTT" means it supports RTT.
2. Stand under an AP, tap it in the list, then **Where I am** (or type x and y from a floor plan). Add a label like "AP near dock". **Save**.
3. Do this for every AP you want to use. For RTT you need **at least 3** RTT APs in range everywhere.
4. With RTT: stand on a marker, choose it (**Scan QR**), and press **Calibrate RTT 15 s**. This measures each AP's fixed distance error. Do it at 2 or 3 markers.

Press **Send to server** at the end so the server and other phones get the same map. Other phones use **Get from server**.

---

## Part 4. Survey walk (fingerprints)

Needed when the phone or the APs do not support RTT. Do it with the **same phone model** you will test with.

1. **Live > Start**.
2. Walk to a marker. Tap **Survey > Scan QR** and scan its label (or type the name).
3. Hold the phone at chest height, the way a picker holds it. Press **Fingerprint 20 s** and stand still.
4. Repeat at every marker. More markers = better accuracy. Also record a few points on the cross-aisles.
5. Press **Send to server**.

Tips:
- Do the survey when the floor looks like it does during work (doors, pallets, people).
- If an AP is moved or replaced, redo the survey near it.
- Fingerprints from one phone model may be less good on another model.

---

## Part 5. Measure accuracy

This is the main test. It answers: "How close is the app's position to the truth?"

1. **Live > Start**. Walk around for a minute so the app settles. Do **not** scan a marker QR at the spot you are going to test: scanning resets the position to the exact truth and makes the result look too good. (Scanning is fine on the **Accuracy** tab: there it only fills in the marker name.)
2. Go to the **Accuracy** tab.
3. Walk to a marker and stand on it. Choose the marker: tap one of the nearby suggestions, type its name, or **Scan QR**.
4. Press **Record 10 s here** and stand still. The app saves 20 samples (estimate and truth).
5. Walk to the next marker by a normal route, not straight across racks. Repeat.
6. Cover at least **20 markers**, in at least 3 aisles. Then do a second round in a different order.

The screen shows:
- **Median error (p50)**: half the samples are closer than this.
- **p90**: 9 out of 10 samples are closer than this.
- **Correct aisle**: how often the app named the right aisle. This matters most for traffic cues.
- **PASS** when correct aisle is 95% or more **and** p90 is 2 m or less. This is the blueprint's week 2 target.

Save the results:
- **Export CSV** shares the file (email, Drive, WhatsApp).
- **Send to server** uploads them; the dashboard shows the summary and has **Download CSV**.
- On a laptop: `python3 tools/accuracy_report.py accuracy.csv` prints tables by source, phone and marker, and the worst markers.

Only report numbers measured this way. Simulation numbers are not floor numbers.

---

## Part 6. Swarm demo

1. Server running (Part 2), app settings filled in, positioning running.
2. **Live > Get traffic cues**: turn on. "Server link" should say connected. The phone now appears on the dashboard as a filled dot.
3. The app gives you a pick list. Walk to the pink circle on the map. Stand there 6 s to finish the pick, or press **Pick done, next**.
4. Set 4 to 8 ghosts on the dashboard so there is traffic in the narrow aisles.
5. Follow the banner on the phone:

| Cue | Banner | Feel | What to do |
| --- | --- | --- | --- |
| Slow | amber, "Slow down: A2 busy" | 2 short buzzes | Walk slower; someone will use that aisle soon. |
| Wait | blue, "Wait N s" | 1 long buzz | Stop at the aisle entrance. Do not enter. |
| Go | green, "Go" | 1 short buzz | The aisle is free. Walk. |
| Reroute | purple, "Avoid A2" | 3 short buzzes | Take another aisle to your pick. |
| New order | purple, "Next pick changed" | 3 short buzzes | Your next pick was swapped to save waiting. Follow the new pink circle. |
| Swarm off | grey | 1 tiny buzz | No server for 5 s. Use your own eyes; no cues will come. |

Practise the buzz patterns first: **Settings > Cues > Practice**.

Safety: cues are a help, not a safety system. People must still look before they enter an aisle.

---

## Problems and fixes

| Problem | Fix |
| --- | --- |
| "Waiting for Wi-Fi data" for a long time | Location must be on. Turn off scan throttling. Scan a QR marker to start at a known point. |
| Position jumps between aisles | Add fingerprints at more markers, especially at aisle ends. Check AP positions on the map. |
| "Wi-Fi scans refused" goes up | Scan throttling is still on (Part 1). |
| Server link "not connected" | Same Wi-Fi? Firewall allows 1883? Broker address starts with `tcp://`? Try a hotspot. |
| Dashboard says "broker not connected" | The swarm container started before Mosquitto. Wait 10 s; it reconnects by itself. |
| "Not in the site map" when scanning | The phone has an old map. **Survey > Get from server**. |
| The app stops in the background | Phone **Settings > Apps > Swarm AMR > Battery > Unrestricted**. |
