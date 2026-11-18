# Glossary

AMR
:   Autonomous Mobile Robot. Warehouse robots that drive themselves and are coordinated by a fleet manager.

Aisle / walkway
:   A path people walk along. Numbered A0, A1, A2… from the left.

Narrow aisle
:   Only one person fits at a time. The engine only plans these.

Cross-aisle
:   The paths at both ends of the aisles that connect them.

Marker
:   A surveyed spot (for example a desk or an aisle end), optionally with a QR code. Ground truth for the accuracy test.

Access point (AP)
:   A Wi-Fi radio on the ceiling or wall. Identified by its BSSID (MAC address).

BSSID / SSID
:   BSSID is the radio's address (`7c:b6:8d:…`). SSID is the Wi-Fi network name.

RSSI
:   Received signal strength, in dBm. −40 is very strong, −90 is very weak.

RTT / FTM
:   Round-Trip Time / Fine Timing Measurement (IEEE 802.11mc). Measures distance to an AP from radio travel time.

Fingerprint
:   A recording of which APs are heard at a spot, and how strongly.

kNN
:   k nearest neighbours: find the recordings most similar to the live scan and average their positions.

Kalman filter
:   A filter that combines a motion model with noisy measurements to give a smooth estimate and its uncertainty.

Map matching
:   Snapping a position to the nearest walkable line.

Sigma (σ)
:   One standard deviation of position error, in metres.

p50 / p90
:   Median and 90th percentile of error: half / nine out of ten readings are within this distance.

Ghost
:   A simulated worker on the server. Makes traffic so one phone can see cues.

Cue / directive
:   The instruction sent to a worker: Normal, Warning, Hold, Go, Reroute, Resequence.

Hold
:   Wait at the aisle entrance for at most 15 s.

Resequence
:   Do a later pick first, to avoid waiting.

DataWedge
:   Zebra's built-in service that sends barcode scans from the hardware scanner to apps.

MQTT
:   A lightweight publish/subscribe messaging protocol. Mosquitto is the broker used here.

Site file
:   The JSON file that describes a place: grid, APs, markers, recordings.
