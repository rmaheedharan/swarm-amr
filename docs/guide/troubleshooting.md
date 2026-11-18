# Troubleshooting

## Android app

| Problem | Fix |
| --- | --- |
| "Finding you…" for a long time | Location must be on. Turn off scan throttling. Record Wi-Fi first. Scan a code to start at a known spot. |
| Yellow "Android is slowing Wi-Fi scans" | Turn off **Wi-Fi scan throttling** in Developer options ([Install](install.md#android-app)). |
| Position jumps between aisles | Record Wi-Fi at more spots, especially at aisle ends. Check the coverage layer for grey and red. Check access point positions (RTT). |
| "Near A or B" | Those spots look alike to Wi-Fi. Record again while turning around, and add spots between them. |
| "Code is not on the map" when scanning | Tap **Add it** to make a marker, or get the newest place file. |
| The app stops in the background | **Settings > Apps > Swarm AMR > Battery > Unrestricted**. |
| Wi-Fi RTT says "no" | The phone does not support RTT. The app uses fingerprints instead; this is normal. |
| Install blocked | Allow installs from your browser or file manager when Android asks. On managed Zebra devices, ask your MDM admin. |

## Server and traffic demo

| Problem | Fix |
| --- | --- |
| "Cannot reach the server" | Same Wi-Fi? Firewall allows 1883 and 8000? Broker address starts with `tcp://`? |
| Phone browser cannot open the dashboard | Office and guest Wi-Fi often block device-to-device traffic (client isolation). Use a travel router or phone hotspot for the demo. |
| Grey "No traffic info" | No heartbeat for 5 s. Check the server window and the broker address. |
| No cues at all | Mark some aisles as **narrow**; only those are planned. Add 4 to 8 ghosts for traffic. |
| `docker compose` not found | Install Docker Desktop, or use `docker-compose` on old installs. |

## Mac app

| Problem | Fix |
| --- | --- |
| macOS will not open the app | See [Install > Mac](install.md#mac-app-swarmlocate), or install with Homebrew. |
| No access points listed | Allow Location: **System Settings > Privacy & Security > Location Services > SwarmLocate**. |
| Always the wrong spot | Try **Fixed correction** after calibrating, or **Order only**. Press **I moved** after moving the laptop. |
