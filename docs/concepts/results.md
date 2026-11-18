# Results and limits

## Two kinds of numbers

| Label | Meaning |
| --- | --- |
| <span class="badge sim">SIMULATION</span> | From the Python simulation of pickers on a grid. Useful to tune the engine. Not proof that it works on a floor. |
| <span class="badge floor">FLOOR</span> | Measured with real devices on a real floor using the accuracy test or the dashboard counters. |

This project never mixes the two.

## Traffic engine <span class="badge sim">SIMULATION</span>

Each number is the average of 10 simulated one-hour shifts. Engine off and engine on use the
same pick lists.

| Scenario | Congestion incidents / h | Head-on deadlocks / h | Average delay per trip | Picks / worker / h |
| --- | --- | --- | --- | --- |
| 5 pickers, walking | **−46%** (141.7 → 76.1) | −39% | −6% (1.9 → 1.7 s) | −2% |
| 5 pickers with carts | **−38%** (138.3 → 85.6) | −27% | −8% (2.5 → 2.3 s) | −2% |
| 10 pickers with carts (stress) | **−20%** (655 → 522) | −13% | +2% (6.5 → 6.6 s) | −4% |

Reading these numbers:

- The main goal, **fewer jams with delay no worse**, is met in the 5-worker cases.
- The **90th percentile delay goes up** (for example 3.7 → 5.0 s with carts) because planned
  holds replace random blocks. Holds are predictable; blocks are not.
- At 10 workers the aisles are simply full; the engine helps less.
- Workers feel about **20 to 40 cues per hour**. Whether that is acceptable must be tested
  with real people.

## Positioning <span class="badge floor">FLOOR</span>

No floor numbers are published in these docs yet. Run the
[accuracy test](../guide/accuracy-test.md) and record the results per site, device model and
method (RTT or fingerprint). The pass bar for traffic cues is:

- **Correct aisle ≥ 95%**, and
- **90th percentile error ≤ 2 m**.

## Known limits

| Limit | Impact | Mitigation |
| --- | --- | --- |
| Android scan throttling (4 scans / 2 min) | Fingerprint updates are too slow | Turn off *Wi-Fi scan throttling* in Developer options. The app warns while it is on. |
| Many phones and APs lack RTT | Only fingerprinting is available | Dense recordings, QR checkpoints; BLE beacons are an optional future addition. |
| Fingerprints age when the floor changes | Accuracy drops over weeks | Re-record near changed racks or moved APs. Coverage layer shows gaps. |
| Look-alike spots | "Near A or B" | Record between them; stand still a few seconds. |
| MacBook antenna differs from phone | Bias of 5 to 10 dB | Automatic or calibrated offset in the Mac app. |
| Demo broker has no authentication | Anyone on the network can publish | Private network only. Add Mosquitto users and TLS for longer pilots. |
| Office Wi-Fi often blocks client-to-client traffic | Phones cannot reach the laptop | Use a travel router or phone hotspot for the demo. |
| Cues are advisory | People may ignore them | The engine detects ignoring and plans around it. |
